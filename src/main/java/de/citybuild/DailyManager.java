package de.citybuild;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Taegliche Belohnung mit Serie (Streak). */
public class DailyManager {

    private static final long HOUR_MS = 3_600_000L;

    private final CityBuildPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public DailyManager(CityBuildPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "daily.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte daily.yml nicht speichern: " + e.getMessage());
        }
    }

    private long cooldownMs() {
        return plugin.getConfig().getLong("daily.cooldown-hours", 24) * HOUR_MS;
    }

    /** Verbleibende Zeit in Millisekunden (0 = jetzt abholbar). */
    public long remaining(UUID uuid) {
        long last = data.getLong(uuid + ".last", 0L);
        long left = last + cooldownMs() - System.currentTimeMillis();
        return Math.max(0L, left);
    }

    public int streak(UUID uuid) {
        return data.getInt(uuid + ".streak", 0);
    }

    /** Belohnung abholen. Gibt die neue Serie zurueck. Vorher remaining() == 0 pruefen. */
    public int claim(UUID uuid) {
        long now = System.currentTimeMillis();
        long last = data.getLong(uuid + ".last", 0L);
        int streak = data.getInt(uuid + ".streak", 0);

        // Serie reisst ab, wenn mehr als ein Cooldown zusaetzlich verstrichen ist
        if (last > 0 && now - last <= cooldownMs() * 2) {
            streak++;
        } else {
            streak = 1;
        }
        data.set(uuid + ".last", now);
        data.set(uuid + ".streak", streak);
        save();
        return streak;
    }

    public double rewardFor(int streak) {
        double base = plugin.getConfig().getDouble("daily.base-reward", 500.0);
        double bonus = plugin.getConfig().getDouble("daily.streak-bonus", 100.0);
        int maxStreak = Math.max(1, plugin.getConfig().getInt("daily.max-streak", 7));
        return base + bonus * (Math.min(streak, maxStreak) - 1);
    }

    public static String formatDuration(long ms) {
        long totalMinutes = ms / 60_000L;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) {
            return hours + " Std. " + minutes + " Min.";
        }
        return Math.max(1, minutes) + " Min.";
    }
}
