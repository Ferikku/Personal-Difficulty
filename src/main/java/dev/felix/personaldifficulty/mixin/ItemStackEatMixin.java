package dev.felix.personaldifficulty.mixin;

import dev.felix.personaldifficulty.PersonalDifficultyMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Detects when a {@link ServerPlayer} finishes eating an Enchanted Golden Apple (the only item
 * that raises max health on Hardcore). Routed to {@link PersonalDifficultyMod#handleEnchantedAppleEaten}
 * which enforces the player's per-difficulty max-hearts cap (Hardcore is capped at 10 hearts, so
 * over-eating a god apple won't exceed it against the player's will). Non-apple items and the
 * client side are ignored for zero overhead.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackEatMixin {
	@Inject(method = "finishUsingItem", at = @At("HEAD"))
	private void personaldifficulty$onFinishUsingItem(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
		if (!level.isClientSide() && ((ItemStack) (Object) this).is(Items.ENCHANTED_GOLDEN_APPLE)) {
			if (entity instanceof ServerPlayer player) {
				PersonalDifficultyMod.handleEnchantedAppleEaten(player);
			}
		}
	}
}