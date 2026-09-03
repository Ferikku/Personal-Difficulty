package dev.felix.personaldifficulty.mixin;

import dev.felix.personaldifficulty.PersonalDifficultyContext;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The linchpin of the whole mod: intercepts {@link LevelAccessor#getDifficulty()} which is read
 * constantly by vanilla code (mob AI, melee damage scaling, hunger/starvation, equipment rolls,
 * spawn logic, etc.). When a {@link PersonalDifficultyContext} scope is active for a
 * {@link ServerPlayer}, this returns that player's personal difficulty instead of the world's.
 *
 * <p>This is a HEAD-inject with a fast null check: on the common path (no player scope active,
 * e.g. mob farms / non-player-adjacent logic) the ThreadLocal holds {@code null} and the real
 * value simply passes through. The only per-call cost is one ThreadLocal lookup.
 */
@Mixin(LevelAccessor.class)
public interface LevelAccessorDifficultyMixin {
	@Inject(method = "getDifficulty", at = @At("HEAD"), cancellable = true)
	private void personaldifficulty$getDifficulty(CallbackInfoReturnable<Difficulty> cir) {
		Difficulty difficulty = PersonalDifficultyContext.currentDifficultyOverride();
		if (difficulty != null) {
			cir.setReturnValue(difficulty);
		}
	}
}
