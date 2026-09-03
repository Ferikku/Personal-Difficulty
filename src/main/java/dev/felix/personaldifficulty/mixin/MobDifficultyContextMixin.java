package dev.felix.personaldifficulty.mixin;

import dev.felix.personaldifficulty.PersonalDifficultyContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Wraps the primary per-tick and per-attack entry points on {@link Mob} so that, while a mob is
 * acting on a player, all {@code level.getDifficulty()} reads see that player's personal difficulty.
 *
 * <p>Performance note: {@link PersonalDifficultyContext#forTarget} is the cheap fast path - it
 * returns a shared no-op {@code Scope} when the mob has no player target (e.g. mob farms and mobs
 * idling/wandering), so mobs that aren't currently targeting a player incur only an instanceof
 * check and an immediate no-op close instead of any ThreadLocal write.
 */
@Mixin(Mob.class)
public abstract class MobDifficultyContextMixin {
	@Shadow
	public abstract LivingEntity getTarget();

	/** Runs mob tick with the difficulty of whatever it's currently targeting. */
	@WrapMethod(method = "tick")
	private void personaldifficulty$tickWithDifficultyContext(Operation<Void> original) {
		try (PersonalDifficultyContext.Scope _ = PersonalDifficultyContext.forTarget(this.getTarget())) {
			original.call();
		}
	}

	/** Runs mob AI with the difficulty of its current target (affects pathing/fall/damage rolls). */
	@WrapMethod(method = "serverAiStep")
	private void personaldifficulty$serverAiStepWithDifficultyContext(Operation<Void> original) {
		try (PersonalDifficultyContext.Scope _ = PersonalDifficultyContext.forTarget(this.getTarget())) {
			original.call();
		}
	}

	/**
	 * Wraps the base melee attack so damage/effect logic inside reads the attacked player's
	 * difficulty. Subclasses that override {@code doHurtTarget} (e.g. Cave Spider) get their own
	 * wrapping mixin, since the override bypasses this base wrapper.
	 */
	@WrapMethod(method = "doHurtTarget")
	private boolean personaldifficulty$doHurtTargetWithDifficultyContext(ServerLevel level, Entity target, Operation<Boolean> original) {
		try (PersonalDifficultyContext.Scope _ = PersonalDifficultyContext.forTarget(target)) {
			return original.call(level, target);
		}
	}
}
