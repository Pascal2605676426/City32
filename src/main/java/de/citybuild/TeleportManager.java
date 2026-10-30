package de.citybuild;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportManager {

    private final CityBuildPlugin plugin;
    private final Map<UUID, BukkitTask> pending = new HashMap<>();

    public TeleportManager(CityBuildPlugin plugin) {
        this.plugin = plugin;
    }

    /** Teleportiert den Spieler (mit Wartezeit, falls konfiguriert). */
    public void teleport(Player player, Location target, String label) {
        int delay = plugin.getConfig().getInt("teleport.delay-seconds", 3);
        UUID uuid = player.getUniqueId();

        BukkitTask old = pending.remove(uuid);
        if (old != null) {
            old.cancel();
        }

        if (delay <= 0 || player.hasPermission("citybuild.tpdelay.bypass")) {
            player.teleportAsync(target);
            plugin.send(player, "&aTeleportiert nach &e" + label + "&a.");
            return;
        }

        Location start = player.getLocation();
        plugin.send(player, "&7Teleport in &e" + delay + "&7 Sekunden - bewege dich nicht!");

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pending.remove(uuid);
            if (!player.isOnline()) {
                return;
            }
            Location now = player.getLocation();
            if (!now.getWorld().equals(start.getWorld()) || now.distanceSquared(start) > 1.0) {
                plugin.send(player, "&cTeleport abgebrochen, du hast dich bewegt.");
                return;
            }
            player.teleportAsync(target);
            plugin.send(player, "&aTeleportiert nach &e" + label + "&a.");
        }, delay * 20L);

        pending.put(uuid, task);
    }
}
