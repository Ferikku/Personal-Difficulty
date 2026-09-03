package dev.felix.personaldifficulty.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.felix.personaldifficulty.PersonalDifficultyContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Wraps {@link Projectile#onHit} so that when a projectile (arrow, fireball, etc.) strikes a
 * {@link ServerPlayer}, the impact damage/effect logic reads that player's personal difficulty.
 * Hits on non-players bypass the scope entirely (no-op path).
 */
@Mixin(Projectile.class)
public abstract class ProjectileDifficultyContextMixin {
	@WrapMethod(method = "onHit")
	private void personaldifficulty$onHitWithDifficultyContext(HitResult hitResult, Operation<Void> original) {
		if (hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof ServerPlayer player) {
			try (PersonalDifficultyContext.Scope _ = PersonalDifficultyContext.forPlayer(player)) {
				original.call(hitResult);
			}
			return;
		}

		original.call(hitResult);
	}
}
