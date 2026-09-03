package dev.felix.personaldifficulty;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Map;
import java.util.UUID;

public final class PersonalDifficultyCommands {
	private PersonalDifficultyCommands() {
	}

	/**
	 * Registers both commands: {@code /pdifficulty} (admin management) and
	 * {@code /pdifficulty_apply} (first-time player self-configuration). Only the server registers
	 * these; {@code environment} distinguishes single/multi/dedicated as needed.
	 */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
		dispatcher.register(root("pdifficulty"));
		dispatcher.register(applyCommand());
	}

	/** Builds the admin-only {@code /pdifficulty} tree: get/set/reset/list/reload. */
	private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
				.requires(PersonalDifficultyCommands::isAdmin);

		root.then(Commands.literal("get")
				.executes(context -> showSelf(context.getSource()))
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> showTarget(context.getSource(), EntityArgument.getPlayer(context, "player")))));

//		ArgumentBuilder<CommandSourceStack, ?> setSelf = Commands.literal("set");
//		addDifficultyChildren(setSelf, (context, difficulty) -> setSelf(context.getSource(), difficulty));
//		root.then(setSelf);

		RequiredArgumentBuilder<CommandSourceStack, ?> setTargetPlayer = Commands.argument("player", EntityArgument.player());
		for (PlayerDifficulty difficulty : PlayerDifficulty.values()) {
			LiteralArgumentBuilder<CommandSourceStack> difficultyLiteral = Commands.literal(difficulty.id())
					.executes(context -> setTarget(context.getSource(), EntityArgument.getPlayer(context, "player"), difficulty, null));

			difficultyLiteral.then(Commands.argument("keepInventory", BoolArgumentType.bool())
					.executes(context -> setTarget(
							context.getSource(),
							EntityArgument.getPlayer(context, "player"),
							difficulty,
							BoolArgumentType.getBool(context, "keepInventory"))));

			setTargetPlayer.then(difficultyLiteral);
		}

		root.then(Commands.literal("set").then(setTargetPlayer));

		root.then(Commands.literal("reset")
				.executes(context -> resetSelf(context.getSource()))
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> resetTarget(context.getSource(), EntityArgument.getPlayer(context, "player")))));

		root.then(Commands.literal("list")
				.executes(context -> listSaved(context.getSource())));

		root.then(Commands.literal("reload")
				.executes(context -> reload(context.getSource())));

		return root;
	}

	/**
	 * Builds {@code /pdifficulty_apply <difficulty> <keepInventory>}, a command restricted to
	 * non-admin first-time setup. It has no {@code requires} gate here - safety is enforced inside
	 * {@link #applyFirstTimeSettings} by rejecting players who already have a saved preference.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> applyCommand() {
		LiteralArgumentBuilder<CommandSourceStack> apply = Commands.literal("pdifficulty_apply");

		for (PlayerDifficulty difficulty : PlayerDifficulty.values()) {
			apply.then(Commands.literal(difficulty.id())
					.then(Commands.argument("keepInventory", BoolArgumentType.bool())
							.executes(context -> applyFirstTimeSettings(
									context.getSource(),
									difficulty,
									BoolArgumentType.getBool(context, "keepInventory")))));
		}

		return apply;
	}

	/**
	 * One-time player self-setup: records the chosen difficulty + keep-inventory and refreshes both
	 * the in-memory cache and the player's max health. Refuses to run a second time for the same
	 * player (their difficulty can only be changed by an admin afterwards).
	 */
	private static int applyFirstTimeSettings(CommandSourceStack source, PlayerDifficulty difficulty, boolean keepInventory) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (PersonalDifficultyMod.store().hasSaved(player.getUUID())) {
			source.sendFailure(Component.literal("Contact an admin to change your Difficulty again"));
			return 0;
		}

		PersonalDifficultyMod.store().set(player.getUUID(), player.getName().getString(), difficulty);
		PersonalDifficultyMod.store().setKeepInventory(player.getUUID(), keepInventory);
		PersonalDifficultyMod.refreshCache(player);
		PersonalDifficultyMod.updatePlayerMaxHealth(player);

		player.sendSystemMessage(Component.literal("Your Difficulty has been set to ")
				.append(Component.literal(difficulty.displayName()).withStyle(ChatFormatting.GOLD))
				.append(Component.literal(" (Keep Inventory: "))
				.append(Component.literal((keepInventory ? "on" : "off")).withStyle(ChatFormatting.GOLD))
				.append(Component.literal(")"))
						);
		return Command.SINGLE_SUCCESS;
	}

	/** Returns true only for sources meeting the configured admin permission level. */
	private static boolean isAdmin(CommandSourceStack source) {
		return Commands.hasPermission(PersonalDifficultyMod.config().adminPermissionCheck()).test(source);
	}

	/** Shows the executing player's own current settings. */
	private static int showSelf(CommandSourceStack source) throws CommandSyntaxException {
		return showTarget(source, source.getPlayerOrException());
	}

	/**
	 * Displays the target's effective difficulty, keep-inventory and heart count. It shows the
	 * effective (resolved) value and marks whether it comes from a saved preference or the server
	 * default/gamerule, so admins can tell the difference at a glance.
	 */
	private static int showTarget(CommandSourceStack source, ServerPlayer player) {
		PlayerDifficulty difficulty = PersonalDifficultyMod.difficultyFor(player);
		boolean explicit = PersonalDifficultyMod.store().getExplicit(player.getUUID()).isPresent();
		String suffix = explicit ? "" : " (server default)";
		Boolean personalKeepInventory = PersonalDifficultyMod.personalKeepInventory(player);
		boolean keepInventory = personalKeepInventory != null
				? personalKeepInventory
				: player.level().getGameRules().get(GameRules.KEEP_INVENTORY);
		String keepInventorySuffix = personalKeepInventory != null ? "" : " (server default)";
		success(source, player.getName().getString() + ": " + difficulty.displayName() + suffix
				+ " (Keep Inventory: " + (keepInventory ? "on" : "off") + keepInventorySuffix + ", " + ((int)player.getMaxHealth())/2 + " Hearts)");
		return Command.SINGLE_SUCCESS;
	}

	/**
	 * Admin command to assign a target player a difficulty (and optionally a keep-inventory
	 * override). Persists the change, then refreshes the lock-free cache and the player's health.
	 */
	private static int setTarget(CommandSourceStack source, ServerPlayer player, PlayerDifficulty difficulty, Boolean keepInventory) {
		PersonalDifficultyMod.store().set(player.getUUID(), player.getName().getString(), difficulty);

		if (keepInventory != null) {
			PersonalDifficultyMod.store().setKeepInventory(player.getUUID(), keepInventory);
		}

		PersonalDifficultyMod.refreshCache(player);
		PersonalDifficultyMod.updatePlayerMaxHealth(player);

		String message = "Set " + player.getName().getString() + " to Difficulty " + difficulty.displayName()
				+ (keepInventory != null ? " (Keep Inventory: " + (keepInventory ? "on" : "off") + ")" : "");
		success(source, message);
		return Command.SINGLE_SUCCESS;
	}

	/** Resets the executing player back to first-time setup (delegates to {@link #resetTarget}). */
	private static int resetSelf(CommandSourceStack source) throws CommandSyntaxException {
		return resetTarget(source, source.getPlayerOrException());
	}

	/**
	 * Admin command to wipe a player's saved preferences, restoring them to server defaults and
	 * re-triggering the first-time setup prompt on their next join. Also refreshes cache + health.
	 */
	private static int resetTarget(CommandSourceStack source, ServerPlayer player) {
		PersonalDifficultyMod.store().reset(player.getUUID());
		PersonalDifficultyMod.refreshCache(player);
		PersonalDifficultyMod.updatePlayerMaxHealth(player);
		PersonalDifficultyMod.scheduleJoinPrompt(player);
		success(source, "Reset Difficulty of " + player.getName().getString());
		return Command.SINGLE_SUCCESS;
	}

	/** Lists every player that has an explicit saved preference (admin overview). */
	private static int listSaved(CommandSourceStack source) {
		Map<UUID, PersonalDifficultyStore.SavedPreference> snapshot = PersonalDifficultyMod.store().snapshot();
		if (snapshot.isEmpty()) {
			success(source, "Difficulty List is empty");
			return Command.SINGLE_SUCCESS;
		}

		success(source, "Saved personal Difficulty Settings:");
		for (PersonalDifficultyStore.SavedPreference preference : snapshot.values()) {
			PlayerDifficulty.byId(preference.difficulty).ifPresent(difficulty ->
					success(source, "- " + preference.name + ": " + difficulty.displayName() + 
							" (Keep Inventory: " + (preference.keepInventory ? "on" : "off") + ", " + preference.maxHearts + " Hearts)"));
		}
		return snapshot.size();
	}

	/** Re-reads config + player preferences from disk and rebuilds all caches (admin). */
	private static int reload(CommandSourceStack source) {
		PersonalDifficultyMod.reloadConfig(source.getServer());
		success(source, "Reloaded Personal Difficulty config");
		return Command.SINGLE_SUCCESS;
	}

	/** Sends a non-broadcast (per-sender) success chat message. */
	private static void success(CommandSourceStack source, String message) {
		source.sendSuccess(() -> Component.literal(message), false);
	}

	/** Helper for a self-set command taking a literal difficulty argument (currently unused). */
	@FunctionalInterface
	private interface DifficultyCommand {
		int run(CommandContext<CommandSourceStack> context, PlayerDifficulty difficulty) throws CommandSyntaxException;
	}
}