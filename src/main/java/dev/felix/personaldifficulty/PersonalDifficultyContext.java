package dev.felix.personaldifficulty;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;

/**
 * Manages a ThreadLocal-based difficulty override. While a {@link Scope} is active, the global
 * {@code LevelAccessor.getDifficulty()} mixin returns the personal difficulty of the player being
 * processed, so vanilla code (mob AI, melee, hunger, projectiles) automatically uses that player's
 * difficulty.
 *
 * <p>This runs on the single server thread and is deliberately allocation-free on the hot path:
 * targets that aren't players, and no-op cases, reuse shared singleton scopes instead of calling
 * {@code new} each time.
 */
public final class PersonalDifficultyContext {
    private static final ThreadLocal<Difficulty> DIFFICULTY = new ThreadLocal<>();

    private PersonalDifficultyContext() {
    }

    /**
     * Opens a scope for the given target if it is a {@link ServerPlayer}; otherwise returns a
     * shared no-op scope (mob farm, non-player target). This is the cheap fast path.
     */
    public static Scope forTarget(Entity target) {
        if (target instanceof ServerPlayer player) {
            return forPlayer(player);
        }
        return Scope.NOOP;
    }

    /**
     * Opens a scope for a specific player, saving the previous ThreadLocal value so scopes can be
     * safely nested (e.g. a projectile hit triggering another hit). The resolved difficulty comes
     * from {@link PersonalDifficultyMod#difficultyFor}, which is lock-free via cache.
     */
    public static Scope forPlayer(ServerPlayer player) {
        Difficulty previous = DIFFICULTY.get();
        DIFFICULTY.set(vanillaDifficultyFor(PersonalDifficultyMod.difficultyFor(player)));
        return new Scope(previous);
    }

    /** Returns the currently overridden difficulty, or {@code null} if none is active. */
    public static Difficulty currentDifficultyOverride() {
        return DIFFICULTY.get();
    }

    /** Maps the mod's player difficulty onto the vanilla enum the mixin overrides use. */
    private static Difficulty vanillaDifficultyFor(PlayerDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> Difficulty.EASY;
            case NORMAL -> Difficulty.NORMAL;
            case HARD, HARDCORE -> Difficulty.HARD;
        };
    }

    /** Auto-closeable scope that restores the previous difficulty on close. */
    public static final class Scope implements AutoCloseable {
        private static final Scope NOOP = new Scope(null, false);

        private final Difficulty previous;
        private final boolean active;

        private Scope(Difficulty previous) {
            this(previous, true);
        }

        private Scope(Difficulty previous, boolean active) {
            this.previous = previous;
            this.active = active;
        }

        @Override
        public void close() {
            // Fast path: no-op scopes (non-player targets) do nothing.
            if (!this.active) {
                return;
            }

            if (this.previous == null) {
                DIFFICULTY.remove();
            } else {
                DIFFICULTY.set(this.previous);
            }
        }
    }
}
