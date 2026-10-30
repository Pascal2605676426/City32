package de.citybuild;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class HomeCommands implements TabExecutor {

    private static final Pattern NAME = Pattern.compile("[a-zA-Z0-9_-]{1,16}");

    private final CityBuildPlugin plugin;

    public HomeCommands(CityBuildPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "&cDieser Befehl ist nur fuer Spieler.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sethome" -> setHome(player, args);
            case "home" -> home(player, args);
            case "delhome" -> delHome(player, args);
            case "homes" -> listHomes(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void setHome(Player player, String[] args) {
        String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home";
        if (!NAME.matcher(name).matches()) {
            plugin.send(player, "&cUngueltiger Name (1-16 Zeichen: Buchstaben, Zahlen, _ und -).");
            return;
        }
        HomeManager homes = plugin.homes();
        Map<String, Location> existing = homes.getHomes(player.getUniqueId());
        int max = homes.maxHomes(player);
        if (!existing.containsKey(name) && existing.size() >= max) {
            plugin.send(player, "&cDu hast bereits das Maximum von &e" + max
                    + "&c Homes. Loesche eins mit &e/delhome <Name>&c.");
            return;
        }
        homes.setHome(player.getUniqueId(), name, player.getLocation());
        plugin.send(player, "&aHome &e" + name + "&a gesetzt.");
    }

    private void home(Player player, String[] args) {
        Map<String, Location> homes = plugin.homes().getHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            plugin.send(player, "&cDu hast noch kein Home. Setze eins mit &e/sethome&c.");
            return;
        }
        String name;
        if (args.length > 0) {
            name = args[0].toLowerCase(Locale.ROOT);
        } else if (homes.containsKey("home")) {
            name = "home";
        } else if (homes.size() == 1) {
            name = homes.keySet().iterator().next();
        } else {
            plugin.send(player, "&7Deine Homes: &e" + String.join("&7, &e", homes.keySet()));
            return;
        }
        Location target = homes.get(name);
        if (target == null) {
            plugin.send(player, "&cHome &e" + name + "&c nicht gefunden. Deine Homes: &e"
                    + String.join("&7, &e", homes.keySet()));
            return;
        }
        plugin.teleports().teleport(player, target, "Home " + name);
    }

    private void delHome(Player player, String[] args) {
        if (args.length != 1) {
            plugin.send(player, "&7Benutzung: &e/delhome <Name>");
            return;
        }
        if (plugin.homes().deleteHome(player.getUniqueId(), args[0])) {
            plugin.send(player, "&7Home &e" + args[0].toLowerCase(Locale.ROOT) + "&7 geloescht.");
        } else {
            plugin.send(player, "&cDieses Home gibt es nicht.");
        }
    }

    private void listHomes(Player player) {
        Map<String, Location> homes = plugin.homes().getHomes(player.getUniqueId());
        int max = plugin.homes().maxHomes(player);
        if (homes.isEmpty()) {
            plugin.send(player, "&7Du hast keine Homes (&e0&7/&e" + max + "&7).");
            return;
        }
        plugin.send(player, "&7Homes (&e" + homes.size() + "&7/&e" + max + "&7): &e"
                + String.join("&7, &e", homes.keySet()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> result = new ArrayList<>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1 && (name.equals("home") || name.equals("delhome"))
                && sender instanceof Player player) {
            result.addAll(plugin.homes().getHomes(player.getUniqueId()).keySet());
        }
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        result.removeIf(s -> !s.startsWith(prefix));
        return result;
    }
}
