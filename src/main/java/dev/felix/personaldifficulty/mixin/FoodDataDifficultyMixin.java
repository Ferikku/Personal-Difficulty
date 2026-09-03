package dev.felix.personaldifficulty.mixin;

import dev.felix.personaldifficulty.PersonalDifficultyContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Wraps {@link FoodData#tick} (runs ~20x/sec per player) so the hunger/starvation code inside,
 * which reads {@code level.getDifficulty()}, uses the player's personal difficulty. This is a
 * hot path; the player-scope caches make the difficulty lookup lock-free.
 */
@Mixin(FoodData.class)
public abstract class FoodDataDifficultyMixin {
	@WrapMethod(method = "tick")
	private void personaldifficulty$tickWithDifficultyContext(ServerPlayer player, Operation<Void> original) {
		try (PersonalDifficultyContext.Scope _ = PersonalDifficultyContext.forPlayer(player)) {
			original.call(player);
		}
	}
}
