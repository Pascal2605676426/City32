package de.citybuild;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** /clan ... und /cc <Nachricht> (Clan-Chat) */
public class ClanCommand implements TabExecutor {

    private static final Pattern TAG = Pattern.compile("[a-zA-Z0-9]{2,5}");

    private final CityBuildPlugin plugin;

    public ClanCommand(CityBuildPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "&cDieser Befehl ist nur fuer Spieler.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("cc")) {
            chat(player, args);
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create", "gruenden" -> create(player, rest);
            case "invite", "einladen" -> invite(player, rest);
            case "accept", "join", "annehmen" -> accept(player);
            case "leave", "verlassen" -> leave(player);
            case "kick" -> kick(player, rest);
            case "disband", "aufloesen" -> disband(player);
            case "info" -> info(player, rest);
            case "list", "liste" -> list(player);
            case "chat" -> chat(player, rest);
            default -> help(player);
        }
        return true;
    }

    private void help(Player player) {
        plugin.sendRaw(player, "&8&m      &r &bClans &8&m      ");
        plugin.sendRaw(player, "&e/clan create <Tag> &7- Clan gruenden (2-5 Zeichen)");
        plugin.sendRaw(player, "&e/clan invite <Spieler> &7- Spieler einladen");
        plugin.sendRaw(player, "&e/clan accept &7- Einladung annehmen");
        plugin.sendRaw(player, "&e/clan leave &7- Clan verlassen");
        plugin.sendRaw(player, "&e/clan kick <Spieler> &7- Mitglied rauswerfen");
        plugin.sendRaw(player, "&e/clan info [Tag] &7- Infos zu einem Clan");
        plugin.sendRaw(player, "&e/clan list &7- Alle Clans");
        plugin.sendRaw(player, "&e/clan disband &7- Clan aufloesen");
        plugin.sendRaw(player, "&e/cc <Nachricht> &7- Clan-Chat");
    }

    private void create(Player player, String[] args) {
        ClanManager clans = plugin.clans();
        if (clans.getClan(player.getUniqueId()) != null) {
            plugin.send(player, "&cDu bist schon in einem Clan.");
            return;
        }
        if (args.length != 1 || !TAG.matcher(args[0]).matches()) {
            plugin.send(player, "&7Benutzung: &e/clan create <Tag> &7(2-5 Buchstaben oder Zahlen)");
            return;
        }
        if (clans.byTag(args[0]) != null) {
            plugin.send(player, "&cDieser Tag ist schon vergeben.");
            return;
        }
        double cost = plugin.getConfig().getDouble("clans.create-cost", 5000.0);
        EconomyManager economy = plugin.economy();
        if (!economy.withdraw(player.getUniqueId(), cost)) {
            plugin.send(player, "&cEin Clan kostet &e" + economy.format(cost) + "&c.");
            return;
        }
        String tag = args[0].toUpperCase(Locale.ROOT);
        clans.create(tag, player.getUniqueId());
        plugin.refreshPlayer(player);
        plugin.send(player, "&aClan &e" + tag + "&a gegruendet! Lade Spieler mit &e/clan invite <Spieler>&a ein.");
    }

    private void invite(Player player, String[] args) {
        ClanManager clans = plugin.clans();
        ClanManager.Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            plugin.send(player, "&cDu bist in keinem Clan.");
            return;
        }
        if (!clan.leader().equals(player.getUniqueId())) {
            plugin.send(player, "&cNur der Anfuehrer darf einladen.");
            return;
        }
        if (args.length != 1) {
            plugin.send(player, "&7Benutzung: &e/clan invite <Spieler>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.send(player, "&cSpieler nicht gefunden (muss online sein).");
            return;
        }
        if (clans.getClan(target.getUniqueId()) != null) {
            plugin.send(player, "&cDieser Spieler ist schon in einem Clan.");
            return;
        }
        int max = plugin.getConfig().getInt("clans.max-members", 10);
        if (clan.members().size() >= max) {
            plugin.send(player, "&cDein Clan ist voll (&e" + max + "&c Mitglieder).");
            return;
        }
        clans.invite(target.getUniqueId(), clan);
        plugin.send(player, "&7Einladung an &e" + target.getName() + "&7 gesendet.");
        plugin.send(target, "&e" + player.getName() + "&7 laedt dich in den Clan &e" + clan.tag()
                + "&7 ein. Annehmen mit &e/clan accept&7 (60 Sekunden).");
    }

    private void accept(Player player) {
        ClanManager clans = plugin.clans();
        if (clans.getClan(player.getUniqueId()) != null) {
            plugin.send(player, "&cDu bist schon in einem Clan.");
            return;
        }
        ClanManager.Clan clan = clans.takeInvite(player.getUniqueId());
        if (clan == null) {
            plugin.send(player, "&cDu hast keine (gueltige) Einladung.");
            return;
        }
        int max = plugin.getConfig().getInt("clans.max-members", 10);
        if (clan.members().size() >= max) {
            plugin.send(player, "&cDieser Clan ist inzwischen voll.");
            return;
        }
        clans.addMember(clan, player.getUniqueId());
        plugin.refreshPlayer(player);
        broadcast(clan, "&e" + player.getName() + "&7 ist dem Clan beigetreten.");
    }

    private void leave(Player player) {
        ClanManager clans = plugin.clans();
        ClanManager.Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            plugin.send(player, "&cDu bist in keinem Clan.");
            return;
        }
        boolean wasLeader = clan.leader().equals(player.getUniqueId());
        clans.removeMember(clan, player.getUniqueId());
        plugin.refreshPlayer(player);
        plugin.send(player, "&7Du hast den Clan &e" + clan.tag() + "&7 verlassen.");
        if (!clan.members().isEmpty()) {
            broadcast(clan, "&e" + player.getName() + "&7 hat den Clan verlassen.");
            if (wasLeader) {
                broadcast(clan, "&7Neuer Anfuehrer ist &e" + nameOf(clan.leader()) + "&7.");
            }
        }
    }

    private void kick(Player player, String[] args) {
        ClanManager clans = plugin.clans();
        ClanManager.Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null || !clan.leader().equals(player.getUniqueId())) {
            plugin.send(player, "&cNur der Anfuehrer eines Clans darf Mitglieder rauswerfen.");
            return;
        }
        if (args.length != 1) {
            plugin.send(player, "&7Benutzung: &e/clan kick <Spieler>");
            return;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[0]);
        }
        if (target == null || !clan.members().contains(target.getUniqueId())) {
            plugin.send(player, "&cDieser Spieler ist nicht in deinem Clan.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.send(player, "&cDu kannst dich nicht selbst rauswerfen. Nutze &e/clan leave&c.");
            return;
        }
        clans.removeMember(clan, target.getUniqueId());
        broadcast(clan, "&e" + args[0] + "&7 wurde aus dem Clan geworfen.");
        Player online = target.getPlayer();
        if (online != null) {
            plugin.refreshPlayer(online);
            plugin.send(online, "&cDu wurdest aus dem Clan &e" + clan.tag() + "&c geworfen.");
        }
    }

    private void disband(Player player) {
        ClanManager clans = plugin.clans();
        ClanManager.Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null || !clan.leader().equals(player.getUniqueId())) {
            plugin.send(player, "&cNur der Anfuehrer eines Clans darf ihn aufloesen.");
            return;
        }
        List<UUID> members = new ArrayList<>(clan.members());
        broadcast(clan, "&cDer Clan &e" + clan.tag() + "&c wurde aufgeloest.");
        clans.disband(clan);
        for (UUID uuid : members) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                plugin.refreshPlayer(online);
            }
        }
    }

    private void info(Player player, String[] args) {
        ClanManager clans = plugin.clans();
        ClanManager.Clan clan;
        if (args.length > 0) {
            clan = clans.byTag(args[0]);
        } else {
            clan = clans.getClan(player.getUniqueId());
        }
        if (clan == null) {
            plugin.send(player, args.length > 0 ? "&cDiesen Clan gibt es nicht." : "&cDu bist in keinem Clan.");
            return;
        }
        List<String> names = new ArrayList<>();
        for (UUID uuid : clan.members()) {
            boolean online = Bukkit.getPlayer(uuid) != null;
            names.add((online ? "&a" : "&7") + nameOf(uuid));
        }
        plugin.sendRaw(player, "&8&m      &r &bClan " + clan.tag() + " &8&m      ");
        plugin.sendRaw(player, "&7Anfuehrer: &e" + nameOf(clan.leader()));
        plugin.sendRaw(player, "&7Mitglieder (&e" + clan.members().size() + "&7): " + String.join("&8, ", names));
    }

    private void list(Player player) {
        List<String> tags = new ArrayList<>();
        for (ClanManager.Clan clan : plugin.clans().all()) {
            tags.add("&e" + clan.tag() + " &7(" + clan.members().size() + ")");
        }
        if (tags.isEmpty()) {
            plugin.send(player, "&7Es gibt noch keine Clans.");
            return;
        }
        plugin.send(player, "&7Clans: " + String.join("&8, ", tags));
    }

    private void chat(Player player, String[] args) {
        ClanManager.Clan clan = plugin.clans().getClan(player.getUniqueId());
        if (clan == null) {
            plugin.send(player, "&cDu bist in keinem Clan.");
            return;
        }
        if (args.length == 0) {
            plugin.send(player, "&7Benutzung: &e/cc <Nachricht>");
            return;
        }
        Component message = plugin.color("&8[&bClan&8] &7" + player.getName() + "&8: ")
                .append(Component.text(String.join(" ", args), NamedTextColor.WHITE));
        for (UUID uuid : clan.members()) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.sendMessage(message);
            }
        }
    }

    private void broadcast(ClanManager.Clan clan, String text) {
        for (UUID uuid : clan.members()) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                plugin.send(online, text);
            }
        }
    }

    private String nameOf(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> result = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("clan")) {
            if (args.length == 1) {
                result.addAll(List.of("create", "invite", "accept", "leave", "kick", "info", "list", "disband", "chat"));
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    result.add(p.getName());
                }
            }
        }
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        result.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return result;
    }
}
