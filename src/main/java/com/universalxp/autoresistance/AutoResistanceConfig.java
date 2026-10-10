package com.universalxp.autoresistance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores which players have switched AutoResistance OFF.
 * Everyone is ON by default. Saved to config/autoresistance.json so it survives restarts.
 */
public final class AutoResistanceConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Set<UUID> DISABLED = ConcurrentHashMap.newKeySet();

	private AutoResistanceConfig() {
	}

	private static class Data {
		List<String> disabledPlayers = new ArrayList<>();
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("autoresistance.json");
	}

	public static boolean isEnabled(UUID player) {
		return !DISABLED.contains(player);
	}

	public static void setEnabled(UUID player, boolean enabled) {
		if (enabled) {
			DISABLED.remove(player);
		} else {
			DISABLED.add(player);
		}
		save();
	}

	public static void load() {
		DISABLED.clear();
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			Data data = GSON.fromJson(reader, Data.class);
			if (data != null && data.disabledPlayers != null) {
				for (String id : data.disabledPlayers) {
					try {
						DISABLED.add(UUID.fromString(id));
					} catch (IllegalArgumentException ignored) {
						// skip a broken entry
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			AutoResistance.LOGGER.warn("Could not read autoresistance.json, using defaults", e);
		}
	}

	private static void save() {
		Data data = new Data();
		for (UUID id : DISABLED) {
			data.disabledPlayers.add(id.toString());
		}
		try {
			Files.createDirectories(file().getParent());
			try (Writer writer = Files.newBufferedWriter(file())) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			AutoResistance.LOGGER.warn("Could not save autoresistance.json", e);
		}
	}
}
