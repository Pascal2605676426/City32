package de.citybuild;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Einfaches Rang-System. Die Raenge selbst stehen in der config.yml, die Zuordnung in ranks.yml. */
public class RankManager {

    private final CityBuildPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public RankManager(CityBuildPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ranks.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte ranks.yml nicht speichern: " + e.getMessage());
        }
    }

    public String defaultKey() {
        return plugin.getConfig().getString("rank-default", "spieler").toLowerCase(Locale.ROOT);
    }

    public boolean exists(String key) {
        return plugin.getConfig().isConfigurationSection("ranks." + key.toLowerCase(Locale.ROOT));
    }

    public Set<String> keys() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("ranks");
        if (section == null) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(section.getKeys(false));
    }

    public String getRankKey(UUID uuid) {
        String key = data.getString(uuid.toString());
        if (key != null && exists(key)) {
            return key.toLowerCase(Locale.ROOT);
        }
        return defaultKey();
    }

    public void setRank(UUID uuid, String key) {
        key = key.toLowerCase(Locale.ROOT);
        if (key.equals(defaultKey())) {
            data.set(uuid.toString(), null);
        } else {
            data.set(uuid.toString(), key);
        }
        save();
    }

    public String name(String key) {
        return plugin.getConfig().getString("ranks." + key + ".name", key);
    }

    public String prefix(String key) {
        return plugin.getConfig().getString("ranks." + key + ".prefix", "");
    }

    public String nameColor(String key) {
        return plugin.getConfig().getString("ranks." + key + ".name-color", "&f");
    }
}
