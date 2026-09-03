package dev.felix.personaldifficulty.mixin;

import dev.felix.personaldifficulty.PersonalDifficultyContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Cave Spider overrides {@link Mob#doHurtTarget} itself and reads {@code level.getDifficulty()}
 * there to decide its poison duration (0s on Easy, 7s on Normal, 15s on Hard). Because that
 * override bypasses the base {@link Mob} wrapper ({@link MobDifficultyContextMixin}), we wrap it
 * here too so the poison a player receives reflects their personal difficulty.
 */
@Mixin(CaveSpider.class)
public abstract class CaveSpiderDifficultyMixin {
	@WrapMethod(method = "doHurtTarget")
	private boolean personaldifficulty$doHurtTargetWithDifficultyContext(ServerLevel level, Entity target, Operation<Boolean> original) {
		try (PersonalDifficultyContext.Scope _ = PersonalDifficultyContext.forTarget(target)) {
			return original.call(level, target);
		}
	}
}
