package dev.felix.personaldifficulty;

import java.util.Locale;
import java.util.Optional;

/**
 * The four selectable personal difficulty levels. Each instance maps to one of the vanilla
 * {@link net.minecraft.world.Difficulty} values used by the mod plus a display name. Note there is
 * deliberately no PEACEFUL option: personal difficulty is for active gameplay, and Easy is the
 * softest level (mirrors vanilla Easy's damage/hunger behavior).
 */
public enum PlayerDifficulty {
	EASY("easy", "Easy"),
	NORMAL("normal", "Normal"),
	HARD("hard", "Hard"),
	HARDCORE("hardcore", "Hardcore");

	private final String id;
	private final String displayName;

	PlayerDifficulty(String id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	/** Stable lowercase id used for JSON serialization and command arguments. */
	public String id() {
		return this.id;
	}

	/** Human-readable name shown in chat messages. */
	public String displayName() {
		return this.displayName;
	}

	/** Looks up a difficulty by its id (case-insensitive). Returns empty for null/unknown ids. */
	public static Optional<PlayerDifficulty> byId(String id) {
		if (id == null) {
			return Optional.empty();
		}

		String normalized = id.toLowerCase(Locale.ROOT);

		for (PlayerDifficulty difficulty : values()) {
			if (difficulty.id.equals(normalized)) {
				return Optional.of(difficulty);
			}
		}

		return Optional.empty();
	}
}
