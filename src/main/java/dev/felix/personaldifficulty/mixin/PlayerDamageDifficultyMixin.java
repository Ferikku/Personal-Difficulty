package dev.felix.personaldifficulty.mixin;

import dev.felix.personaldifficulty.PersonalDifficultyContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Wraps {@link Player#hurtServer} so that damage-scaling code inside (which reads
 * {@code level.getDifficulty()} to halve damage on Easy / boost on Hard) uses the damaged player's
 * personal difficulty. Fast path: for non-{@link ServerPlayer} targets the original runs unchanged.
 */
@Mixin(Player.class)
public abstract class PlayerDamageDifficultyMixin {
	@WrapMethod(method = "hurtServer")
	private boolean personaldifficulty$hurtServerWithDifficultyContext(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
		Player self = (Player) (Object) this;
		if (self instanceof ServerPlayer player) {
			try (PersonalDifficultyContext.Scope _ = PersonalDifficultyContext.forPlayer(player)) {
				return original.call(level, source, damage);
			}
		}

		return original.call(level, source, damage);
	}
}
