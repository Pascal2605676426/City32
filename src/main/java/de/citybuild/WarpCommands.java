package de.citybuild;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class WarpCommands implements TabExecutor {

    private static final Pattern NAME = Pattern.compile("[a-zA-Z0-9_-]{1,16}");

    private final CityBuildPlugin plugin;

    public WarpCommands(CityBuildPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        if (name.equals("warps")) {
            var names = plugin.warps().names();
            if (names.isEmpty()) {
                plugin.send(sender, "&7Es gibt noch keine Warps.");
            } else {
                plugin.send(sender, "&7Warps: &e" + String.join("&7, &e", names));
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.send(sender, "&cDieser Befehl ist nur fuer Spieler.");
            return true;
        }

        switch (name) {
            case "spawn" -> {
                Location spawn = plugin.warps().getSpawn();
                if (spawn == null) {
                    plugin.send(player, "&cEs ist noch kein Spawn gesetzt.");
                } else {
                    plugin.teleports().teleport(player, spawn, "Spawn");
                }
            }
            case "setspawn" -> {
                if (!player.hasPermission("citybuild.admin")) {
                    plugin.send(player, "&cDazu hast du keine Berechtigung.");
                    return true;
                }
                plugin.warps().setSpawn(player.getLocation());
                plugin.send(player, "&aSpawn gesetzt.");
            }
            case "warp" -> {
                if (args.length != 1) {
                    plugin.send(player, "&7Benutzung: &e/warp <Name> &7- Liste mit &e/warps");
                    return true;
                }
                Location target = plugin.warps().getWarp(args[0]);
                if (target == null) {
                    plugin.send(player, "&cDiesen Warp gibt es nicht. Liste mit &e/warps&c.");
                } else {
                    plugin.teleports().teleport(player, target, "Warp " + args[0].toLowerCase(Locale.ROOT));
                }
            }
            case "setwarp" -> {
                if (!player.hasPermission("citybuild.admin")) {
                    plugin.send(player, "&cDazu hast du keine Berechtigung.");
                    return true;
                }
                if (args.length != 1 || !NAME.matcher(args[0]).matches()) {
                    plugin.send(player, "&7Benutzung: &e/setwarp <Name> &7(1-16 Zeichen: Buchstaben, Zahlen, _ und -)");
                    return true;
                }
                plugin.warps().setWarp(args[0], player.getLocation());
                plugin.send(player, "&aWarp &e" + args[0].toLowerCase(Locale.ROOT) + "&a gesetzt.");
            }
            case "delwarp" -> {
                if (!player.hasPermission("citybuild.admin")) {
                    plugin.send(player, "&cDazu hast du keine Berechtigung.");
                    return true;
                }
                if (args.length != 1) {
                    plugin.send(player, "&7Benutzung: &e/delwarp <Name>");
                    return true;
                }
                if (plugin.warps().deleteWarp(args[0])) {
                    plugin.send(player, "&7Warp &e" + args[0].toLowerCase(Locale.ROOT) + "&7 geloescht.");
                } else {
                    plugin.send(player, "&cDiesen Warp gibt es nicht.");
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> result = new ArrayList<>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1 && (name.equals("warp") || name.equals("delwarp"))) {
            result.addAll(plugin.warps().names());
        }
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        result.removeIf(s -> !s.startsWith(prefix));
        return result;
    }
}
