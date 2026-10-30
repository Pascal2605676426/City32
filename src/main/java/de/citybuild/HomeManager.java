package de.citybuild;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class HomeManager {

    private final CityBuildPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public HomeManager(CityBuildPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte homes.yml nicht speichern: " + e.getMessage());
        }
    }

    public Map<String, Location> getHomes(UUID uuid) {
        Map<String, Location> result = new TreeMap<>();
        ConfigurationSection section = data.getConfigurationSection(uuid.toString());
        if (section == null) {
            return result;
        }
        for (String name : section.getKeys(false)) {
            try {
                Location loc = section.getLocation(name);
                if (loc != null && loc.getWorld() != null) {
                    result.put(name, loc);
                }
            } catch (Exception ignored) {
                // Welt existiert nicht mehr - Home ueberspringen
            }
        }
        return result;
    }

    public Location getHome(UUID uuid, String name) {
        return getHomes(uuid).get(name.toLowerCase());
    }

    public void setHome(UUID uuid, String name, Location loc) {
        data.set(uuid + "." + name.toLowerCase(), loc.clone());
        save();
    }

    public boolean deleteHome(UUID uuid, String name) {
        String path = uuid + "." + name.toLowerCase();
        if (!data.contains(path)) {
            return false;
        }
        data.set(path, null);
        save();
        return true;
    }

    /** Maximale Anzahl Homes: Standardwert oder hoechste Permission citybuild.homes.&lt;n&gt;. */
    public int maxHomes(Player player) {
        int max = plugin.getConfig().getInt("homes.default-max", 3);
        for (int i = 1; i <= 100; i++) {
            if (i > max && player.hasPermission("citybuild.homes." + i)) {
                max = i;
            }
        }
        return max;
    }
}
