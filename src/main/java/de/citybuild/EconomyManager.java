package de.citybuild;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {

    private final CityBuildPlugin plugin;
    private final File file;
    private final Map<UUID, Double> balances = new HashMap<>();
    private boolean dirty;

    public EconomyManager(CityBuildPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
        load();
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yml.getConfigurationSection("balances");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), section.getDouble(key));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ungueltige UUID in balances.yml: " + key);
            }
        }
    }

    public void save() {
        if (!dirty) {
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            yml.set("balances." + entry.getKey(), entry.getValue());
        }
        try {
            yml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte balances.yml nicht speichern: " + e.getMessage());
        }
    }

    public boolean hasAccount(UUID uuid) {
        return balances.containsKey(uuid);
    }

    public void ensureAccount(UUID uuid) {
        if (!balances.containsKey(uuid)) {
            set(uuid, plugin.getConfig().getDouble("economy.start-balance", 1000.0));
        }
    }

    public double get(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }

    public void set(UUID uuid, double amount) {
        balances.put(uuid, round(Math.max(0, amount)));
        dirty = true;
    }

    public void deposit(UUID uuid, double amount) {
        set(uuid, get(uuid) + amount);
    }

    /** Zieht Geld ab. Gibt false zurueck, wenn nicht genug Guthaben vorhanden ist. */
    public boolean withdraw(UUID uuid, double amount) {
        if (get(uuid) + 0.0001 < amount) {
            return false;
        }
        set(uuid, get(uuid) - amount);
        return true;
    }

    public String format(double amount) {
        String symbol = plugin.getConfig().getString("economy.currency-symbol", "$");
        return String.format(Locale.GERMANY, "%,.2f", amount) + symbol;
    }

    public List<Map.Entry<UUID, Double>> top(int limit) {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(balances.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list.subList(0, Math.min(limit, list.size()));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
