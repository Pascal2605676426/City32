package de.citybuild;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /farmwelt, /nether, /end */
public class WorldCommands implements TabExecutor {

    private final CityBuildPlugin plugin;

    public WorldCommands(CityBuildPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "&cDieser Befehl ist nur fuer Spieler.");
            return true;
        }
        WorldManager worlds = plugin.worlds();

        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "farmwelt" -> {
                World world = worlds.farmWorld();
                if (world == null) {
                    plugin.send(player, "&cDie Farmwelt ist gerade nicht verfuegbar.");
                    return true;
                }
                int radius = plugin.getConfig().getInt("worlds.farmworld.random-radius", 2500);
                randomTeleport(player, world, radius, "Farmwelt");
            }
            case "nether" -> {
                World world = worlds.findByEnvironment(World.Environment.NETHER);
                if (world == null) {
                    plugin.send(player, "&cDer Nether ist gerade nicht verfuegbar.");
                    return true;
                }
                int radius = plugin.getConfig().getInt("worlds.nether-random-radius", 1500);
                randomTeleport(player, world, radius, "Nether");
            }
            case "end" -> {
                World world = worlds.findByEnvironment(World.Environment.THE_END);
                if (world == null) {
                    plugin.send(player, "&cDas End ist gerade nicht verfuegbar.");
                    return true;
                }
                plugin.teleports().teleport(player, world.getSpawnLocation(), "End");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void randomTeleport(Player player, World world, int radius, String label) {
        plugin.send(player, "&7Suche einen sicheren Platz in der &e" + label + "&7 ...");
        plugin.worlds().findSafe(world, radius, 15, location -> {
            if (!player.isOnline()) {
                return;
            }
            if (location == null) {
                plugin.send(player, "&cEs wurde kein sicherer Platz gefunden. Versuche es nochmal.");
                return;
            }
            Location target = location;
            plugin.teleports().teleport(player, target, label);
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
