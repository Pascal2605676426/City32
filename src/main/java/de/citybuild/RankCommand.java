package de.citybuild;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /rang <Spieler> <Rang>  und  /rang list */
public class RankCommand implements TabExecutor {

    private final CityBuildPlugin plugin;

    public RankCommand(CityBuildPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("citybuild.admin")) {
            plugin.send(sender, "&cDazu hast du keine Berechtigung.");
            return true;
        }
        RankManager ranks = plugin.ranks();

        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            plugin.send(sender, "&7Verfuegbare Raenge: &e" + String.join("&7, &e", ranks.keys()));
            return true;
        }
        if (args.length != 2) {
            plugin.send(sender, "&7Benutzung: &e/rang <Spieler> <Rang> &7oder &e/rang list");
            return true;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[0]);
        }
        if (target == null) {
            plugin.send(sender, "&cSpieler nicht gefunden.");
            return true;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        if (!ranks.exists(key)) {
            plugin.send(sender, "&cDiesen Rang gibt es nicht. Liste mit &e/rang list&c.");
            return true;
        }
        ranks.setRank(target.getUniqueId(), key);
        plugin.send(sender, "&e" + args[0] + "&7 hat jetzt den Rang &e" + ranks.name(key) + "&7.");

        Player online = target.getPlayer();
        if (online != null) {
            plugin.refreshPlayer(online);
            plugin.send(online, "&7Dein Rang ist jetzt &e" + ranks.name(key) + "&7.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.add("list");
            for (Player p : Bukkit.getOnlinePlayers()) {
                result.add(p.getName());
            }
        } else if (args.length == 2) {
            result.addAll(plugin.ranks().keys());
        }
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        result.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return result;
    }
}
