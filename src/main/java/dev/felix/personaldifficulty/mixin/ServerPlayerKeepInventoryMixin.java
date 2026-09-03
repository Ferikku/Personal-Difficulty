package dev.felix.personaldifficulty.mixin;

import dev.felix.personaldifficulty.PersonalDifficultyMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * {@code ServerPlayer} reads {@link GameRules#KEEP_INVENTORY} in three places relevant to
 * death/respawn; see {@link PlayerKeepInventoryMixin} for the other two ({@code dropEquipment},
 * {@code getBaseExperienceReward}), which both turned out to be declared on
 * {@code net.minecraft.world.entity.player.Player}, not here - confirmed by Mixin's own
 * InvalidInjectionException when they were targeted at this class instead.
 *
 * Only {@code restoreFrom(ServerPlayer, boolean)} - whether to copy the old player's
 * inventory/XP/score onto the respawned one - is genuinely declared on {@code ServerPlayer}
 * itself: by its own signature (a {@code ServerPlayer} parameter) it only makes sense between two
 * server player instances, so there's no generic {@code Player} version of it to have overridden.
 *
 * This redirect overrides that one read for the acting {@code ServerPlayer}: if that player has
 * a saved personal Keep Inventory value, it sees that value regardless of the world's actual
 * gamerule; every other player still sees the real value.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerKeepInventoryMixin {

	/**
	 * Replaces a read of the world's KEEP_INVENTORY gamerule during {@code restoreFrom} with the
	 * acting player's personal value when one is saved; otherwise the real gamerule value is kept.
	 */
	@Redirect(
			method = "restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"
			)
	)
	private Object personaldifficulty$keepInventoryForRespawnTransfer(GameRules gameRules, GameRule<?> gameRule) {
		Object realValue = gameRules.get(gameRule);

		if (gameRule == GameRules.KEEP_INVENTORY) {
			ServerPlayer player = (ServerPlayer) (Object) this;
			Boolean personalValue = PersonalDifficultyMod.personalKeepInventory(player);
			if (personalValue != null) {
				return personalValue;
			}
		}

		return realValue;
	}
}
