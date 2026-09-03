package dev.felix.personaldifficulty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent per-player preference store, serialized to {@code players.json}. This is the source of
 * truth on disk; the hot gameplay path reads a lock-free in-memory cache instead (see
 * {@link PersonalDifficultyMod}), so this class is only touched on startup, on explicit
 * set/reset/reload, and on save.
 *
 * <p>All mutating/reading methods are {@code synchronized} on the store instance for safe access
 * from the (single) server thread and any save/load operations.
 */
public final class PersonalDifficultyStore {
	public static final int MIN_HEARTS = 3;    // Hardcore floor: never drop below 3 hearts on death
	public static final int MAX_HEARTS = 10;   // Hardcore cap: max 10 hearts (vanilla default)
	public static final int DEFAULT_HEARTS = 10;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	// Every access goes through a `synchronized` method on this store instance, so a plain HashMap
	// is enough - a ConcurrentHashMap's own locking would just be redundant on top of that.
	private final Map<UUID, SavedPreference> preferences = new HashMap<>();

	public PersonalDifficultyStore(Path path) {
		this.path = path;
	}

	/** Reads all preferences from disk into memory; creates an empty file if it doesn't exist yet. */
	public synchronized void load() {
		this.preferences.clear();

		if (Files.notExists(this.path)) {
			this.save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(this.path)) {
			SaveFile saveFile = GSON.fromJson(reader, SaveFile.class);
			if (saveFile == null || saveFile.players == null) {
				return;
			}

			for (Map.Entry<String, SavedPreference> entry : saveFile.players.entrySet()) {
				try {
					UUID uuid = UUID.fromString(entry.getKey());
					SavedPreference preference = entry.getValue();
					// Skip any entry whose difficulty string is no longer a valid PlayerDifficulty.
					if (preference != null && PlayerDifficulty.byId(preference.difficulty).isPresent()) {
						this.preferences.put(uuid, preference);
					}
				} catch (IllegalArgumentException ignored) {
					PersonalDifficultyMod.LOGGER.warn("Skipping invalid personal difficulty entry for UUID {}", entry.getKey());
				}
			}
		} catch (IOException | JsonParseException exception) {
			PersonalDifficultyMod.LOGGER.warn("Could not read personal difficulty player preferences", exception);
		}
	}

	/** Writes all in-memory preferences to disk, sorted by UUID for stable/diff-friendly output. */
	public synchronized void save() {
		SaveFile saveFile = new SaveFile();
		this.preferences.entrySet().stream()
				.sorted(Comparator.comparing(entry -> entry.getKey().toString()))
				.forEach(entry -> saveFile.players.put(entry.getKey().toString(), entry.getValue()));

		try {
			Files.createDirectories(this.path.getParent());
			try (Writer writer = Files.newBufferedWriter(this.path)) {
				GSON.toJson(saveFile, writer);
			}
		} catch (IOException exception) {
			PersonalDifficultyMod.LOGGER.warn("Could not save personal difficulty player preferences", exception);
		}
	}

	/** Returns the player's saved difficulty, or the fallback if they have none saved. */
	public synchronized PlayerDifficulty getOrDefault(UUID uuid, PlayerDifficulty fallback) {
		return this.getExplicit(uuid).orElse(fallback);
	}

	/** Returns the player's explicitly saved difficulty, or empty if they have none saved. */
	public synchronized Optional<PlayerDifficulty> getExplicit(UUID uuid) {
		SavedPreference preference = this.preferences.get(uuid);
		if (preference == null) {
			return Optional.empty();
		}

		return PlayerDifficulty.byId(preference.difficulty);
	}

	/** Sets a player's keep-inventory override and persists. Returns false if the player has no entry. */
	public synchronized boolean setKeepInventory(UUID uuid, boolean keepInventory) {
		SavedPreference preference = this.preferences.get(uuid);
		if (preference == null) {
			return false;
		}

		preference.keepInventory = keepInventory;
		this.save();
		return true;
	}

	/** Returns whether the player's saved keep-inventory override is on (false if none saved). */
	public synchronized boolean getKeepInventory(UUID uuid) {
		SavedPreference preference = this.preferences.get(uuid);
		return preference != null && preference.keepInventory;
	}

	/** Returns true if the player has any saved preference entry at all. */
	public synchronized boolean hasSaved(UUID uuid) {
		return this.preferences.containsKey(uuid);
	}

	/** Returns the player's max hearts (Hardcore), defaulting to {@link #DEFAULT_HEARTS}. */
	public synchronized int getMaxHearts(UUID uuid) {
		SavedPreference preference = this.preferences.get(uuid);
		return preference != null ? preference.maxHearts : DEFAULT_HEARTS;
	}

	/** Updates a player's max hearts (Hardcore), clamped to [MIN_HEARTS, MAX_HEARTS], and persists. */
	public synchronized void setMaxHearts(UUID uuid, int hearts) {
		SavedPreference preference = this.preferences.get(uuid);
		if (preference != null) {
			preference.maxHearts = Math.max(MIN_HEARTS, Math.min(MAX_HEARTS, hearts));
			this.save();
		}
	}

	/** Records a player's name + difficulty (creating the entry if needed) and persists. */
	public synchronized void set(UUID uuid, String name, PlayerDifficulty difficulty) {
		SavedPreference preference = this.preferences.getOrDefault(uuid, new SavedPreference());
		preference.name = name;
		preference.difficulty = difficulty.id();
		preference.maxHearts = Math.max(MIN_HEARTS, Math.min(MAX_HEARTS, preference.maxHearts));
		this.preferences.put(uuid, preference);
		this.save();
	}

	/** Removes a player's saved preferences entirely (backs them out of personal difficulty) and persists. */
	public synchronized void reset(UUID uuid) {
		this.preferences.remove(uuid);
		this.save();
	}

	/** Returns a defensive copy of all stored preferences (for the admin list command). */
	public synchronized Map<UUID, SavedPreference> snapshot() {
		return new LinkedHashMap<>(this.preferences);
	}

	public static final class SaveFile {
		public int version = 1;
		public Map<String, SavedPreference> players = new LinkedHashMap<>();
	}

	public static final class SavedPreference {
		public String name = "";
		public String difficulty = PlayerDifficulty.NORMAL.id();
		public int maxHearts = DEFAULT_HEARTS;
		public boolean keepInventory = false;
	}
}