package com.github.zeropointnothing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class ConfigLoader {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("itemsbegone_config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Config CONFIG;

    public static void loadConfig() {
        try {
            if (Files.notExists(CONFIG_PATH)) {
                ItemsBegone.LOGGER.warn("Config did not exist, so it was created with default values!");
                CONFIG = new Config(new Config.TeamList(new ArrayList<>()), false);

                // set default teams
                CONFIG.blacklist.addTeam("global", new ArrayList<>(), new ArrayList<>());
                //
                saveConfig();
                return;
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                CONFIG = GSON.fromJson(reader, Config.class);

                // Validate the config.
                if (CONFIG.delete_on_deny == null) {
                    throw new JsonParseException("Required config value 'delete_on_deny' is not present!");
                }
                if (CONFIG.blacklist == null) {
                    throw new JsonParseException("Required config value 'blacklist' is not present!");
                }
                if (CONFIG.blacklist.teams == null) {
                    throw new JsonParseException("Required config value 'blacklist.teams' is not present!");
                }
                // Validate teams as well
                for (int i=0; i<CONFIG.blacklist.teams.size(); i++) {
                    Config.TeamConfig team = CONFIG.blacklist.teams.get(i);
                    if (
                            team.item_blacklist == null
                            || team.namespace_blacklist == null
                            || team.name == null
                    ) {
                        throw new JsonParseException("Team '" + (i+1) + "' within config is malformed!");
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config", e);
        } catch (JsonParseException e) {
            throw new RuntimeException("Failed to load config, as it was malformed!", e);
        }
    }

    public static void saveConfig() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(CONFIG, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config", e);
        }
    }
}
