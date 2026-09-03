package dev.felix.personaldifficulty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.PermissionCheck;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server config ({@code personal-difficulty.json}) for the mod's admin-facing settings.
 * Currently only holds the permission level required to run the admin {@code /pdifficulty}
 * management commands.
 */
public final class PersonalDifficultyConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Permission level required for admin commands; default 2 = game master (level-2 op). */
	public int adminPermissionLevel = 2;

	/**
	 * Loads the config from disk, writing a defaults file if the path doesn't exist yet. On parse
	 * failure it falls back to a fresh defaults instance rather than crashing the server.
	 */
	public static PersonalDifficultyConfig load(Path path) {
		if (Files.notExists(path)) {
			PersonalDifficultyConfig config = new PersonalDifficultyConfig();
			config.save(path);
			return config;
		}

		try (Reader reader = Files.newBufferedReader(path)) {
			PersonalDifficultyConfig config = GSON.fromJson(reader, PersonalDifficultyConfig.class);
			if (config == null) {
				config = new PersonalDifficultyConfig();
			}
			config.save(path);
			return config;
		} catch (IOException | JsonParseException exception) {
			PersonalDifficultyMod.LOGGER.warn("Could not read personal difficulty config; using defaults", exception);
			return new PersonalDifficultyConfig();
		}
	}

	/** Writes the current settings back to disk (pretty-printed JSON). */
	public void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			PersonalDifficultyMod.LOGGER.warn("Could not save personal difficulty config", exception);
		}
	}

	/** Translates the configured integer permission level into a {@link PermissionCheck} for command checks. */
	public PermissionCheck adminPermissionCheck() {
		return switch (this.adminPermissionLevel) {
			case 0 -> Commands.LEVEL_ALL;
			case 1 -> Commands.LEVEL_MODERATORS;
			case 2 -> Commands.LEVEL_GAMEMASTERS;
			case 3 -> Commands.LEVEL_ADMINS;
			default -> Commands.LEVEL_OWNERS;
		};
	}
}
