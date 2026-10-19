package com.sulphate.chatcolor2.managers;

import com.sulphate.chatcolor2.main.ChatColor;
import com.sulphate.chatcolor2.utils.CompatabilityUtils;
import com.sulphate.chatcolor2.utils.Config;
import com.sulphate.chatcolor2.utils.ConfigUtils;
import com.sulphate.chatcolor2.utils.Reloadable;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigsManager implements Reloadable {

    private final ConfigUtils configUtils;

    private final HashMap<String, YamlConfiguration> configs;
    // Player configs are kept separate from the plugin configs, as they must survive a reload. Clearing them would
    // leave online players without a config to save to, causing saves to fail until they rejoined.
    private final Map<UUID, YamlConfiguration> playerConfigs;

    public ConfigsManager(ConfigUtils configUtils) {
        this.configUtils = configUtils;
        configs = new HashMap<>();
        playerConfigs = new ConcurrentHashMap<>();

        reload();
    }

    // (re)loads all configs.
    public void reload() {
        configUtils.clearCache();
        configs.clear();

        for (Config config : Config.values()) {
            String fileName = config.getFilename();

            if (config.equals(Config.GUI)) {
                if (CompatabilityUtils.isHexLegacy() && !configUtils.configExists(fileName)) {
                    configs.put(fileName, configUtils.getConfigOrCopyDefault(fileName, "legacy-gui.yml"));
                    continue;
                }
            }

            configs.put(fileName, configUtils.getConfigOrCopyDefault(fileName));
        }
    }

    // (re)loads a specific config.
    public void reloadSingle(Config config) {
        String fileName = config.getFilename();
        configs.put(fileName, configUtils.getConfigOrCopyDefault(fileName));
    }

    // Returns a given config.
    public YamlConfiguration getConfig(Config config) {
        return configs.get(config.getFilename());
    }

    // Saves a config.
    public void saveConfig(Config config) {
        configUtils.saveConfig(config.getFilename());
    }

    // Loads a player config.
    public void loadPlayerConfig(UUID uuid) {
        String filePath = getPlayerConfigPath(uuid);
        YamlConfiguration config = configUtils.getConfigOrCreateBlank(filePath);

        if (config != null) {
            playerConfigs.put(uuid, config);
        }
    }

    // Gets a player config, loading it from disk if it is not currently held in memory. This can happen if the plugin
    // has been reloaded, or if a player's data was loaded before the config was (re)created.
    public YamlConfiguration getPlayerConfig(UUID uuid) {
        YamlConfiguration config = playerConfigs.get(uuid);

        if (config == null) {
            loadPlayerConfig(uuid);
            config = playerConfigs.get(uuid);
        }

        return config;
    }

    // Drops a player config from memory (e.g. when a player leaves).
    public void unloadPlayerConfig(UUID uuid) {
        playerConfigs.remove(uuid);
    }

    public static String getPlayerConfigPath(UUID uuid) {
        return "players" + File.separator + uuid + ".yml";
    }

}
