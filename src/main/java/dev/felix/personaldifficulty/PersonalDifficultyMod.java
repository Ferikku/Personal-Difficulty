package dev.felix.personaldifficulty;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core mod entry point. Owns the persistent {@link PersonalDifficultyStore} and a lock-free,
 * in-memory cache of every online player's resolved difficulty so the hot gameplay path
 * (called dozens of times per second per player) never touches the synchronized store or disk.
 */
public final class PersonalDifficultyMod implements ModInitializer {
    public static final String MOD_ID = "personal_difficulty";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Identifier HARDCORE_HEALTH_MODIFIER_ID = Identifier.fromNamespaceAndPath(MOD_ID, "hardcore_max_health");

    /** How many ticks after joining to show the difficulty-picker dialog for new players. */
    private static final int JOIN_PROMPT_DELAY_TICKS = 20;

    private static PersonalDifficultyConfig config = new PersonalDifficultyConfig();
    private static PersonalDifficultyStore store;

    /**
     * Cache: player UUID -> resolved personal difficulty. Updated on join / set / reset / reload.
     * Reads here are lock-free (ConcurrentHashMap) and avoid the synchronized store on hot paths.
     */
    private static final Map<UUID, PlayerDifficulty> difficultyCache = new ConcurrentHashMap<>();
    /** Cache: player UUID -> personal keep-inventory override (only present when saved). */
    private static final Map<UUID, Boolean> keepInventoryCache = new ConcurrentHashMap<>();

    private static final Map<UUID, Integer> pendingPrompts = new ConcurrentHashMap<>();
    private static Boolean lastServerDifficultyLocked = null;

    @Override
    public void onInitialize() {
        Path configDirectory = FabricLoader.getInstance().getConfigDir().resolve("personal-difficulty");
        config = PersonalDifficultyConfig.load(configDirectory.resolve("config.json"));
        store = new PersonalDifficultyStore(configDirectory.resolve("players.json"));
        store.load();

        CommandRegistrationCallback.EVENT.register(PersonalDifficultyCommands::register);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(PersonalDifficultyMod::allowDamage);
        ServerTickEvents.END_SERVER_TICK.register(PersonalDifficultyMod::endServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(_ -> store.save());

        // Cache a player's settings the moment they join so later hot-path reads never hit the store.
        ServerPlayConnectionEvents.JOIN.register((handler, _, _) -> {
            ServerPlayer player = handler.getPlayer();
            cachePlayer(player);
            updatePlayerMaxHealth(player);

            if (!store.hasSaved(player.getUUID())) {
                scheduleJoinPrompt(player);
            }
        });

        ServerPlayerEvents.COPY_FROM.register((_, newPlayer, alive) -> {
            // COPY_FROM also runs for non-death player replacements, such as returning from the End.
            cachePlayer(newPlayer);
            if (!alive && !isServerDifficultyLocked(newPlayer) && difficultyFor(newPlayer) == PlayerDifficulty.HARDCORE) {
                int currentHearts = store.getMaxHearts(newPlayer.getUUID());
                int newHearts = Math.max(PersonalDifficultyStore.MIN_HEARTS, currentHearts - 1);
                store.setMaxHearts(newPlayer.getUUID(), newHearts);
            }

            updatePlayerMaxHealth(newPlayer);
        });

        LOGGER.info("Loaded Personal Difficulty");
    }

    /** Accessor for the mod's server config settings. */
    public static PersonalDifficultyConfig config() {
        return config;
    }

    /** Accessor for the persistent per-player preference store. */
    public static PersonalDifficultyStore store() {
        return store;
    }

    /**
     * Populates the lock-free caches for a player from the authoritative store. Called whenever
     * a player's settings are read from disk or mutated, so the caches never go stale.
     */
    private static void cachePlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (isServerDifficultyLocked(player)) {
            difficultyCache.remove(uuid);
            keepInventoryCache.remove(uuid);
            return;
        }

        difficultyCache.put(uuid, store.getOrDefault(uuid, vanillaDifficultyFor(player.level().getDifficulty())));

        if (store.hasSaved(uuid)) {
            keepInventoryCache.put(uuid, store.getKeepInventory(uuid));
        } else {
            keepInventoryCache.remove(uuid);
        }
    }

    /**
     * Reloads config.json from disk and immediately re-applies it (max health, effective
     * difficulty) to every currently-online player, instead of waiting for their next death,
     * respawn, or apple eaten to pick the change up.
     */
    public static void reloadConfig(MinecraftServer server) {
        Path configDirectory = FabricLoader.getInstance().getConfigDir().resolve("personal-difficulty");
        config = PersonalDifficultyConfig.load(configDirectory.resolve("config.json"));

        // Server may have been unlocked/locked in a way that invalidates caches; rebuild from scratch.
        rebuildCache(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            updatePlayerMaxHealth(player);
        }
    }

    /** Rebuilds the per-player caches for every currently online player. */
    private static void rebuildCache(MinecraftServer server) {
        difficultyCache.clear();
        keepInventoryCache.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            cachePlayer(player);
        }
    }

    /**
     * Fast path: returns the player's personal difficulty from the lock-free cache. This is the
     * method invoked on every damage event, mob tick targeting, food tick, and projectile hit,
     * so it must not block on the store's lock. If the cache is absent (rare, e.g. during startup
     * or after a reset before re-join) it falls back to the store / vanilla default.
     */
    public static PlayerDifficulty difficultyFor(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerDifficulty cached = difficultyCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        if (store == null || isServerDifficultyLocked(player)) {
            return vanillaDifficultyFor(player.level().getDifficulty());
        }

        return store.getOrDefault(uuid, vanillaDifficultyFor(player.level().getDifficulty()));
    }

    /** Maps a vanilla server difficulty onto the mod's coarser player difficulty tiers. */
    private static PlayerDifficulty vanillaDifficultyFor(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL, EASY -> PlayerDifficulty.EASY;
            case NORMAL -> PlayerDifficulty.NORMAL;
            case HARD -> PlayerDifficulty.HARD;
        };
    }

    /**
     * Returns the player's personal keep-inventory override, or {@code null} if they have none
     * (in which case the vanilla server gamerule applies). Lock-free via cache.
     */
    public static Boolean personalKeepInventory(ServerPlayer player) {
        if (store == null || isServerDifficultyLocked(player)) {
            return null;
        }
        return keepInventoryCache.get(player.getUUID());
    }

    /**
     * Re-syncs a player's difficulty and keep-inventory caches from the store. Call this after any
     * direct store mutation (set / reset / setKeepInventory / hearts), so the lock-free hot path
     * stays consistent without having to hit the store.
     */
    public static void refreshCache(ServerPlayer player) {
        cachePlayer(player);
    }

    /** Gives a Hardcore player +1 heart when they eat an enchanted golden apple (cap at MAX_HEARTS). */
    public static void handleEnchantedAppleEaten(ServerPlayer player) {
        if (!isServerDifficultyLocked(player) && difficultyFor(player) == PlayerDifficulty.HARDCORE) {
            int currentHearts = store.getMaxHearts(player.getUUID());
            if (currentHearts < PersonalDifficultyStore.MAX_HEARTS) {
                int newHearts = currentHearts + 1;
                store.setMaxHearts(player.getUUID(), newHearts);
                updatePlayerMaxHealth(player);
            }
        }
    }

    /**
     * Applies the Hardcore max-health attribute modifier (or removes it when leaving Hardcore),
     * then clamps the player's current health to their new maximum. Called on join, change,
     * death loss, and apple gain.
     */
    public static void updatePlayerMaxHealth(ServerPlayer player) {
        AttributeInstance maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr == null) return;

        maxHealthAttr.removeModifier(HARDCORE_HEALTH_MODIFIER_ID);

        if (difficultyFor(player) == PlayerDifficulty.HARDCORE) {
            int maxHearts = store.getMaxHearts(player.getUUID());
            double targetHealth = maxHearts * 2.0D;
            double modifierValue = targetHealth - 20.0D;

            if (modifierValue != 0.0D) {
                maxHealthAttr.addPermanentModifier(new AttributeModifier(
                        HARDCORE_HEALTH_MODIFIER_ID,
                        modifierValue,
                        AttributeModifier.Operation.ADD_VALUE
                ));
            }
        }

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Queues the difficulty-picker dialog to be shown to a player shortly after joining. */
    public static void scheduleJoinPrompt(ServerPlayer player) {
        pendingPrompts.putIfAbsent(player.getUUID(), JOIN_PROMPT_DELAY_TICKS);
    }

    /**
     * Enforces the per-difficulty starvation floor. Returns false to block lethal starvation at the
     * floor health for Easy/Normal, while Hard/Hardcore (floor < 0) let starvation be lethal.
     */
    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayer player)) {
            return true;
        }

        PlayerDifficulty difficulty = difficultyFor(player);
        if (DamageClassifier.isStarvation(source)) {
            double floor = vanillaStarvationHealthFloor(difficulty);
            return floor < 0.0D || player.getHealth() > (float) floor;
        }

        return true;
    }

    /** The HP below which starvation stops dealing damage; negative means "no floor / lethal". */
    private static double vanillaStarvationHealthFloor(PlayerDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 10.0D;
            case NORMAL -> 1.0D;
            case HARD, HARDCORE -> -1.0D;
        };
    }

    /** Per-tick maintenance: watch for difficulty-lock changes and count down join prompts. */
    private static void endServerTick(MinecraftServer server) {
        syncDifficultyState(server);
        tickJoinPrompts(server);
    }

    /**
     * Detects transitions in whether the server is difficulty-locked (hardcore world or peaceful
     * server) and, when the lock flips, refreshes all player caches and health. Runs once per tick
     * but short-circuits immediately when the lock state hasn't changed.
     */
    private static void syncDifficultyState(MinecraftServer server) {
        boolean locked = isServerDifficultyLocked(server);
        if (lastServerDifficultyLocked != null && lastServerDifficultyLocked == locked) {
            return;
        }

        lastServerDifficultyLocked = locked;

        // The locked state changed: caches may now be wrong, so rebuild them.
        rebuildCache(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            updatePlayerMaxHealth(player);
        }

        if (!locked) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!store.hasSaved(player.getUUID())) {
                    scheduleJoinPrompt(player);
                }
            }
        }
    }

    /** Counts down and eventually shows the difficulty-picker dialog to new players. */
    private static void tickJoinPrompts(MinecraftServer server) {
        for (Map.Entry<UUID, Integer> entry : pendingPrompts.entrySet()) {
            UUID uuid = entry.getKey();
            int ticksRemaining = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || player.hasDisconnected() || store.hasSaved(uuid)) {
                pendingPrompts.remove(uuid);
                continue;
            }

            if (isServerDifficultyLocked(server)) {
                continue;
            }

            if (ticksRemaining <= 1) {
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack().withSuppressedOutput(),
                        "dialog show " + player.getScoreboardName() + " personal_difficulty:settings"
                );
                pendingPrompts.remove(uuid);
            } else {
                pendingPrompts.replace(uuid, ticksRemaining, ticksRemaining - 1);
            }
        }
    }

    /** Whether the difficulty is locked for the server a specific player belongs to. */
    private static boolean isServerDifficultyLocked(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return server != null && isServerDifficultyLocked(server);
    }

    /** The personal-difficulty system is bypassed when the server is hardcore or set to peaceful. */
    private static boolean isServerDifficultyLocked(MinecraftServer server) {
        return server.isHardcore() || server.getWorldData().getDifficulty() == Difficulty.PEACEFUL;
    }
}
