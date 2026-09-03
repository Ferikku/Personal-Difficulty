package dev.felix.personaldifficulty;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * Classifies {@link DamageSource}s for the mod. Currently only used to detect starvation damage so
 * {@link PersonalDifficultyMod} can enforce each player's per-difficulty starvation floor.
 *
 * <p>Hostile-mob / explosion classification was previously used by an effect-adjustment pass that
 * has been removed (mob effects are now driven by vanilla logic reading the overridden
 * {@code level.getDifficulty()}), so those helpers are gone to keep the hot path lean.
 */
public final class DamageClassifier {
	private DamageClassifier() {
	}

	/** Returns true if the damage source is starvation (the player is running out of food). */
	public static boolean isStarvation(DamageSource source) {
		return source.is(DamageTypes.STARVE);
	}
}
