package dev.felix.personaldifficulty.mixin;

import dev.felix.personaldifficulty.PersonalDifficultyMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Companion to {@link ServerPlayerKeepInventoryMixin}. Two of the three {@link GameRules#KEEP_INVENTORY}
 * reads this mod needs to override turn out to be declared on {@link Player} itself, not on
 * {@link ServerPlayer} - each confirmed by Mixin's own InvalidInjectionException when it was
 * targeted at {@code ServerPlayer} instead:
 *
 * - {@code dropEquipment(ServerLevel)} - whether to scatter the dying player's items.
 * - {@code getBaseExperienceReward(ServerLevel)} - whether/how much XP orb reward the killer gets;
 *   Player overrides this to size it off the dying player's own level, which is Player-general
 *   logic, not something ServerPlayer adds on top.
 *
 * Only {@code restoreFrom(ServerPlayer, boolean)} - which by its own signature only makes sense
 * between two {@code ServerPlayer} instances - stays on {@code ServerPlayer} in the companion
 * mixin. {@code @Mixin} only injects into methods present in the exact class(es) it names, which
 * is why this needs to be split out rather than living alongside that one.
 */
@Mixin(Player.class)
public abstract class PlayerKeepInventoryMixin {

	@Redirect(
			method = "dropEquipment(Lnet/minecraft/server/level/ServerLevel;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"
			)
	)
	private Object personaldifficulty$keepInventoryForEquipmentDrop(GameRules gameRules, GameRule<?> gameRule) {
		return this.personaldifficulty$overrideIfPersonalKeepInventory(gameRules, gameRule);
	}

	@Redirect(
			method = "getBaseExperienceReward(Lnet/minecraft/server/level/ServerLevel;)I",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"
			)
	)
	private Object personaldifficulty$keepInventoryForExperienceReward(GameRules gameRules, GameRule<?> gameRule) {
		return this.personaldifficulty$overrideIfPersonalKeepInventory(gameRules, gameRule);
	}

	/**
	 * Runs the real lookup first so every other gamerule (and every non-{@code ServerPlayer}
	 * {@code Player}) is completely unaffected. For {@link GameRules#KEEP_INVENTORY}, a saved
	 * personal value wins in either direction, so players can opt out even when the server gamerule
	 * is globally enabled.
	 */
	private Object personaldifficulty$overrideIfPersonalKeepInventory(GameRules gameRules, GameRule<?> gameRule) {
		Object realValue = gameRules.get(gameRule);

		if (gameRule == GameRules.KEEP_INVENTORY) {
			Player self = (Player) (Object) this;
			if (self instanceof ServerPlayer player) {
				Boolean personalValue = PersonalDifficultyMod.personalKeepInventory(player);
				if (personalValue != null) {
					return personalValue;
				}
			}
		}

		return realValue;
	}
}
