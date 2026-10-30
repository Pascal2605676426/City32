package de.citybuild;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

public class WarpManager {

    private final CityBuildPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public WarpManager(CityBuildPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "warps.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte warps.yml nicht speichern: " + e.getMessage());
        }
    }

    private Location read(String path) {
        try {
            Location loc = data.getLocation(path);
            return (loc != null && loc.getWorld() != null) ? loc : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Spawn-Position; faellt auf den Weltspawn der ersten Welt zurueck. */
    public Location getSpawn() {
        Location spawn = read("spawn");
        if (spawn != null) {
            return spawn;
        }
        if (Bukkit.getWorlds().isEmpty()) {
            return null;
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public void setSpawn(Location loc) {
        data.set("spawn", loc.clone());
        save();
    }

    public Location getWarp(String name) {
        return read("warps." + name.toLowerCase());
    }

    public void setWarp(String name, Location loc) {
        data.set("warps." + name.toLowerCase(), loc.clone());
        save();
    }

    public boolean deleteWarp(String name) {
        String path = "warps." + name.toLowerCase();
        if (!data.contains(path)) {
            return false;
        }
        data.set(path, null);
        save();
        return true;
    }

    public Set<String> names() {
        ConfigurationSection section = data.getConfigurationSection("warps");
        if (section == null) {
            return new TreeSet<>();
        }
        return new TreeSet<>(section.getKeys(false));
    }
}
