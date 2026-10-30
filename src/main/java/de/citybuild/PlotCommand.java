package de.citybuild;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /plot ... - Grundstuecksverwaltung im Stil von PlotSquared. */
public class PlotCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("claim", "auto", "home", "visit", "list", "info", "add",
            "remove", "deny", "undeny", "setowner", "clear", "delete", "merge", "unmerge", "flag", "biome", "welt");
    private static final String[] DIR_NAMES = {"Norden", "Osten", "Sueden", "Westen"};

    private final CityBuildPlugin plugin;

    public PlotCommand(CityBuildPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "&cDieser Befehl ist nur fuer Spieler.");
            return true;
        }
        if (plugin.plots().world() == null) {
            plugin.send(player, "&cDie Plot-Welt ist nicht verfuegbar. Pruefe die Server-Konsole.");
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "claim", "buy", "kaufen" -> claim(player);
            case "auto" -> auto(player);
            case "home", "h" -> home(player, rest);
            case "visit", "v", "besuchen" -> visit(player, rest);
            case "list", "liste" -> list(player);
            case "info", "i" -> info(player);
            case "add", "trust" -> member(player, rest, true);
            case "remove", "untrust" -> member(player, rest, false);
            case "deny" -> deny(player, rest, true);
            case "undeny" -> deny(player, rest, false);
            case "setowner", "so" -> setOwner(player, rest);
            case "clear", "leeren" -> clear(player, rest);
            case "delete", "unclaim", "dispose", "loeschen" -> delete(player, rest);
            case "merge", "verbinden" -> merge(player, rest, true);
            case "unmerge", "trennen" -> merge(player, rest, false);
            case "flag", "f" -> flag(player, rest);
            case "biome", "biom" -> biome(player, rest);
            case "welt", "world", "spawn" -> {
                plugin.teleports().teleport(player, plugin.plots().worldSpawn(), "Plot-Welt");
            }
            default -> help(player);
        }
        return true;
    }

    private void help(Player player) {
        plugin.sendRaw(player, "&8&m      &r &bGrundstuecke &8&m      ");
        plugin.sendRaw(player, "&e/plot auto &7- Naechstes freies Grundstueck kaufen und hinteleportieren");
        plugin.sendRaw(player, "&e/plot claim &7- Grundstueck kaufen, auf dem du stehst");
        plugin.sendRaw(player, "&e/plot home [Nr] &7- Zu deinem Grundstueck");
        plugin.sendRaw(player, "&e/plot visit <Spieler> [Nr] &7- Grundstueck eines Spielers besuchen");
        plugin.sendRaw(player, "&e/plot list &7- Deine Grundstuecke");
        plugin.sendRaw(player, "&e/plot info &7- Infos zum aktuellen Grundstueck");
        plugin.sendRaw(player, "&e/plot add|remove <Spieler> &7- Bau-Erlaubnis geben/entziehen");
        plugin.sendRaw(player, "&e/plot deny|undeny <Spieler> &7- Spieler aussperren/zulassen");
        plugin.sendRaw(player, "&e/plot setowner <Spieler> &7- Grundstueck uebertragen");
        plugin.sendRaw(player, "&e/plot clear &7- Grundstueck leeren");
        plugin.sendRaw(player, "&e/plot delete &7- Grundstueck abgeben (Teilrueckerstattung)");
        plugin.sendRaw(player, "&e/plot merge|unmerge [Richtung] &7- Grundstuecke verbinden/trennen");
        plugin.sendRaw(player, "&e/plot flag <set|remove|list> ... &7- Einstellungen (pvp, mob-spawning ...)");
        plugin.sendRaw(player, "&e/plot biome <Biom> &7- Biom aendern");
        plugin.sendRaw(player, "&e/plot welt &7- Zur Plot-Welt");
    }

    // ---------- Hilfen ----------

    private PlotManager.Plot here(Player player) {
        return plugin.plots().plotAt(player.getLocation());
    }

    /** Das Grundstueck, auf dem der Spieler steht, wenn er es verwalten darf; sonst null (mit Meldung). */
    private PlotManager.Plot manageable(Player player) {
        PlotManager.Plot plot = here(player);
        if (plot == null) {
            plugin.send(player, "&cDu musst auf einem vergebenen Grundstueck stehen.");
            return null;
        }
        if (!plot.owner.equals(player.getUniqueId()) && !player.hasPermission("citybuild.bypass")) {
            plugin.send(player, "&cDas ist nicht dein Grundstueck.");
            return null;
        }
        return plot;
    }

    private OfflinePlayer target(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private String nameOf(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    private int parseDir(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "n", "nord", "norden", "north" -> 0;
            case "o", "e", "ost", "osten", "east" -> 1;
            case "s", "sued", "sueden", "sued.", "sud", "south" -> 2;
            case "w", "west", "westen" -> 3;
            default -> -1;
        };
    }

    /** Blickrichtung des Spielers als 0 = Norden, 1 = Osten, 2 = Sueden, 3 = Westen. */
    private int facing(Player player) {
        float yaw = ((player.getLocation().getYaw() % 360f) + 360f) % 360f;
        if (yaw >= 315f || yaw < 45f) {
            return 2;
        }
        if (yaw < 135f) {
            return 3;
        }
        if (yaw < 225f) {
            return 0;
        }
        return 1;
    }

    // ---------- Kaufen ----------

    private void claim(Player player) {
        PlotManager plots = plugin.plots();
        int[] id = plots.idAt(player.getLocation());
        if (id == null) {
            plugin.send(player, "&cDu musst auf einem Grundstueck der Plot-Welt stehen. Nutze &e/plot auto&c.");
            return;
        }
        PlotManager.Plot existing = plots.get(id[0], id[1]);
        if (existing != null) {
            plugin.send(player, existing.owner.equals(player.getUniqueId())
                    ? "&cDieses Grundstueck gehoert dir bereits."
                    : "&cDieses Grundstueck gehoert &e" + nameOf(existing.owner) + "&c.");
            return;
        }
        if (!buyPlot(player, id[0], id[1])) {
            return;
        }
        plugin.send(player, "&aGrundstueck gekauft! ID: &e" + id[0] + ";" + id[1]);
    }

    private boolean buyPlot(Player player, int x, int z) {
        PlotManager plots = plugin.plots();
        EconomyManager economy = plugin.economy();
        int max = plugin.getConfig().getInt("plots.max-per-player", 5);
        if (!player.hasPermission("citybuild.plots.unlimited")
                && plots.getOwned(player.getUniqueId()).size() >= max) {
            plugin.send(player, "&cDu hast bereits das Maximum von &e" + max + "&c Grundstuecken.");
            return false;
        }
        double price = plugin.getConfig().getDouble("plots.price", 2500.0);
        if (!economy.withdraw(player.getUniqueId(), price)) {
            plugin.send(player, "&cDu brauchst &e" + economy.format(price) + "&c, hast aber nur &e"
                    + economy.format(economy.get(player.getUniqueId())) + "&c.");
            return false;
        }
        plots.claim(x, z, player.getUniqueId());
        return true;
    }

    private void auto(Player player) {
        PlotManager plots = plugin.plots();
        int maxRadius = plugin.getConfig().getInt("plots.auto-max-radius", 200);
        for (int r = 0; r <= maxRadius; r++) {
            for (int x = -r; x <= r; x++) {
                int step = (Math.abs(x) == r) ? 1 : Math.max(1, 2 * r);
                for (int z = -r; z <= r; z += step) {
                    if (plots.get(x, z) != null) {
                        continue;
                    }
                    if (!buyPlot(player, x, z)) {
                        return;
                    }
                    PlotManager.Plot plot = plots.get(x, z);
                    plugin.send(player, "&aGrundstueck &e" + x + ";" + z + "&a gekauft.");
                    plugin.teleports().teleport(player, plots.home(plot), "dein Grundstueck");
                    return;
                }
            }
        }
        plugin.send(player, "&cEs wurde kein freies Grundstueck gefunden.");
    }

    // ---------- Teleport ----------

    private void home(Player player, String[] args) {
        List<PlotManager.Plot> owned = plugin.plots().getOwned(player.getUniqueId());
        if (owned.isEmpty()) {
            plugin.send(player, "&cDu hast noch kein Grundstueck. Hole dir eins mit &e/plot auto&c.");
            return;
        }
        int index = 1;
        if (args.length > 0) {
            try {
                index = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                plugin.send(player, "&cBitte eine Nummer angeben, z.B. &e/plot home 2&c.");
                return;
            }
        }
        if (index < 1 || index > owned.size()) {
            plugin.send(player, "&cDu hast nur &e" + owned.size() + "&c Grundstueck(e).");
            return;
        }
        plugin.teleports().teleport(player, plugin.plots().home(owned.get(index - 1)), "Grundstueck " + index);
    }

    private void visit(Player player, String[] args) {
        if (args.length < 1) {
            plugin.send(player, "&7Benutzung: &e/plot visit <Spieler> [Nr]");
            return;
        }
        OfflinePlayer target = target(args[0]);
        if (target == null) {
            plugin.send(player, "&cSpieler nicht gefunden.");
            return;
        }
        List<PlotManager.Plot> owned = plugin.plots().getOwned(target.getUniqueId());
        if (owned.isEmpty()) {
            plugin.send(player, "&e" + args[0] + "&c hat kein Grundstueck.");
            return;
        }
        int index = 1;
        if (args.length > 1) {
            try {
                index = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                plugin.send(player, "&cBitte eine Nummer angeben.");
                return;
            }
        }
        if (index < 1 || index > owned.size()) {
            plugin.send(player, "&e" + args[0] + "&c hat nur &e" + owned.size() + "&c Grundstueck(e).");
            return;
        }
        PlotManager.Plot plot = owned.get(index - 1);
        if (plot.denied.contains(player.getUniqueId()) && !player.hasPermission("citybuild.bypass")) {
            plugin.send(player, "&cDu bist auf diesem Grundstueck gesperrt.");
            return;
        }
        plugin.teleports().teleport(player, plugin.plots().home(plot), "Grundstueck von " + args[0]);
    }

    private void list(Player player) {
        PlotLayout l = plugin.plots().layout();
        List<PlotManager.Plot> owned = plugin.plots().getOwned(player.getUniqueId());
        if (owned.isEmpty()) {
            plugin.send(player, "&7Du besitzt noch keine Grundstuecke.");
            return;
        }
        plugin.sendRaw(player, "&8&m      &r &bDeine Grundstuecke &8&m      ");
        int n = 1;
        for (PlotManager.Plot plot : owned) {
            plugin.sendRaw(player, "&e" + n + "&7. ID &e" + plot.x + ";" + plot.z + "&7 bei X: &e"
                    + (l.min(plot.x) + l.size / 2) + "&7 Z: &e" + (l.min(plot.z) + l.size / 2)
                    + (plot.mergeEast || plot.mergeSouth ? " &8(verbunden)" : ""));
            n++;
        }
    }

    private void info(Player player) {
        PlotManager plots = plugin.plots();
        PlotManager.Plot plot = here(player);
        if (plot == null) {
            int[] id = plots.idAt(player.getLocation());
            if (id != null) {
                plugin.send(player, "&7Grundstueck &e" + id[0] + ";" + id[1] + "&7 ist &afrei&7. Kaufe es mit &e/plot claim&7.");
            } else {
                plugin.send(player, "&7Du stehst auf einer Strasse.");
            }
            return;
        }
        plugin.sendRaw(player, "&8&m      &r &bGrundstueck " + plot.x + ";" + plot.z + " &8&m      ");
        plugin.sendRaw(player, "&7Besitzer: &e" + nameOf(plot.owner));
        plugin.sendRaw(player, "&7Darf bauen: &e" + names(plot.trusted));
        plugin.sendRaw(player, "&7Gesperrt: &e" + names(plot.denied));
        StringBuilder flags = new StringBuilder();
        for (String f : PlotManager.BOOL_FLAGS) {
            if (flags.length() > 0) {
                flags.append("&8, ");
            }
            flags.append("&7").append(f).append("=").append(plots.flagBool(plot, f) ? "&atrue" : "&cfalse");
        }
        plugin.sendRaw(player, "&7Flags: " + flags);
    }

    private String names(java.util.Set<UUID> set) {
        if (set.isEmpty()) {
            return "niemand";
        }
        List<String> out = new ArrayList<>();
        for (UUID uuid : set) {
            out.add(nameOf(uuid));
        }
        return String.join(", ", out);
    }

    // ---------- Mitglieder ----------

    private void member(Player player, String[] args, boolean add) {
        if (args.length != 1) {
            plugin.send(player, "&7Benutzung: &e/plot " + (add ? "add" : "remove") + " <Spieler>");
            return;
        }
        PlotManager.Plot plot = manageable(player);
        if (plot == null) {
            return;
        }
        OfflinePlayer target = target(args[0]);
        if (target == null) {
            plugin.send(player, "&cSpieler nicht gefunden.");
            return;
        }
        if (target.getUniqueId().equals(plot.owner)) {
            plugin.send(player, "&cDas ist der Besitzer.");
            return;
        }
        for (PlotManager.Plot p : plugin.plots().group(plot)) {
            if (add) {
                p.trusted.add(target.getUniqueId());
                p.denied.remove(target.getUniqueId());
            } else {
                p.trusted.remove(target.getUniqueId());
            }
        }
        plugin.plots().save();
        plugin.send(player, add
                ? "&e" + args[0] + "&a darf jetzt auf diesem Grundstueck bauen."
                : "&e" + args[0] + "&7 darf hier nicht mehr bauen.");
    }

    private void deny(Player player, String[] args, boolean add) {
        if (args.length != 1) {
            plugin.send(player, "&7Benutzung: &e/plot " + (add ? "deny" : "undeny") + " <Spieler>");
            return;
        }
        PlotManager.Plot plot = manageable(player);
        if (plot == null) {
            return;
        }
        OfflinePlayer target = target(args[0]);
        if (target == null) {
            plugin.send(player, "&cSpieler nicht gefunden.");
            return;
        }
        if (target.getUniqueId().equals(plot.owner)) {
            plugin.send(player, "&cDen Besitzer kannst du nicht aussperren.");
            return;
        }
        for (PlotManager.Plot p : plugin.plots().group(plot)) {
            if (add) {
                p.denied.add(target.getUniqueId());
                p.trusted.remove(target.getUniqueId());
            } else {
                p.denied.remove(target.getUniqueId());
            }
        }
        plugin.plots().save();
        if (add) {
            plugin.send(player, "&e" + args[0] + "&7 darf dieses Grundstueck nicht mehr betreten.");
            Player online = target.getPlayer();
            if (online != null && plugin.plots().plotAt(online.getLocation()) != null
                    && plugin.plots().group(plot).contains(plugin.plots().plotAt(online.getLocation()))) {
                Location spawn = plugin.warps().getSpawn();
                if (spawn != null) {
                    online.teleportAsync(spawn);
                    plugin.send(online, "&cDu wurdest von diesem Grundstueck verwiesen.");
                }
            }
        } else {
            plugin.send(player, "&e" + args[0] + "&a darf das Grundstueck wieder betreten.");
        }
    }

    private void setOwner(Player player, String[] args) {
        if (args.length != 1) {
            plugin.send(player, "&7Benutzung: &e/plot setowner <Spieler>");
            return;
        }
        PlotManager.Plot plot = manageable(player);
        if (plot == null) {
            return;
        }
        OfflinePlayer target = target(args[0]);
        if (target == null) {
            plugin.send(player, "&cSpieler nicht gefunden.");
            return;
        }
        plugin.economy().ensureAccount(target.getUniqueId());
        plugin.plots().setOwner(plot, target.getUniqueId());
        plugin.send(player, "&aDas Grundstueck gehoert jetzt &e" + args[0] + "&a.");
        Player online = target.getPlayer();
        if (online != null) {
            plugin.send(online, "&e" + player.getName() + "&7 hat dir ein Grundstueck uebertragen.");
        }
    }

    // ---------- Leeren und Loeschen ----------

    private void clear(Player player, String[] args) {
        PlotManager.Plot plot = manageable(player);
        if (plot == null) {
            return;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("bestaetigen")) {
            plugin.send(player, "&cAchtung: Dabei wird alles auf dem Grundstueck entfernt! Bestaetige mit &e/plot clear bestaetigen&c.");
            return;
        }
        plugin.send(player, "&7Grundstueck wird geleert ...");
        plugin.plots().clear(plugin.plots().groupRects(plot),
                () -> plugin.send(player, "&aDas Grundstueck wurde geleert."));
    }

    private void delete(Player player, String[] args) {
        PlotManager plots = plugin.plots();
        PlotManager.Plot plot = manageable(player);
        if (plot == null) {
            return;
        }
        if (plots.hasLinks(plot)) {
            plugin.send(player, "&cDieses Grundstueck ist verbunden. Trenne es zuerst mit &e/plot unmerge&c.");
            return;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("bestaetigen")) {
            plugin.send(player, "&cAchtung: Das Grundstueck wird geleert und freigegeben! Bestaetige mit &e/plot delete bestaetigen&c.");
            return;
        }
        double percent = plugin.getConfig().getDouble("plots.refund-percent", 50);
        double refund = plugin.getConfig().getDouble("plots.price", 2500.0) * percent / 100.0;
        UUID owner = plot.owner;
        List<int[]> rects = plots.rectOf(plot);
        plots.remove(plot);
        plugin.economy().ensureAccount(owner);
        plugin.economy().deposit(owner, refund);
        plots.clear(rects, null);
        plugin.send(player, "&aGrundstueck abgegeben. Rueckerstattung: &e" + plugin.economy().format(refund));
    }

    // ---------- Verbinden ----------

    private void merge(Player player, String[] args, boolean merge) {
        PlotManager plots = plugin.plots();
        PlotManager.Plot plot = manageable(player);
        if (plot == null) {
            return;
        }
        int dir = args.length > 0 ? parseDir(args[0]) : facing(player);
        if (dir < 0) {
            plugin.send(player, "&7Richtung: &enorden&7, &eosten&7, &esueden&7 oder &ewest&7.");
            return;
        }
        if (merge) {
            double cost = plugin.getConfig().getDouble("plots.merge-price", 0.0);
            if (cost > 0 && plugin.economy().get(player.getUniqueId()) < cost) {
                plugin.send(player, "&cVerbinden kostet &e" + plugin.economy().format(cost) + "&c.");
                return;
            }
            String error = plots.merge(plot, dir);
            if (error != null) {
                plugin.send(player, "&c" + error);
                return;
            }
            if (cost > 0) {
                plugin.economy().withdraw(player.getUniqueId(), cost);
            }
            plugin.send(player, "&aGrundstuecke nach &e" + DIR_NAMES[dir] + "&a verbunden.");
        } else {
            String error = plots.unmerge(plot, dir);
            if (error != null) {
                plugin.send(player, "&c" + error);
                return;
            }
            plugin.send(player, "&7Verbindung nach &e" + DIR_NAMES[dir] + "&7 getrennt.");
        }
    }

    // ---------- Flags ----------

    private void flag(Player player, String[] args) {
        PlotManager plots = plugin.plots();
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            plugin.send(player, "&7Boolean-Flags: &e" + String.join("&7, &e", PlotManager.BOOL_FLAGS));
            plugin.send(player, "&7Text-Flags: &e" + String.join("&7, &e", PlotManager.TEXT_FLAGS));
            plugin.send(player, "&7Benutzung: &e/plot flag set <Flag> <Wert> &7oder &e/plot flag remove <Flag>");
            return;
        }
        PlotManager.Plot plot = manageable(player);
        if (plot == null) {
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length < 2) {
            plugin.send(player, "&7Benutzung: &e/plot flag set <Flag> <Wert> &7oder &e/plot flag remove <Flag>");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        boolean isBool = PlotManager.BOOL_FLAGS.contains(name);
        boolean isText = PlotManager.TEXT_FLAGS.contains(name);
        if (!isBool && !isText) {
            plugin.send(player, "&cUnbekanntes Flag. Liste mit &e/plot flag list&c.");
            return;
        }
        if (action.equals("remove") || action.equals("delete")) {
            for (PlotManager.Plot p : plots.group(plot)) {
                p.flags.remove(name);
            }
            plots.save();
            plugin.send(player, "&7Flag &e" + name + "&7 zurueckgesetzt.");
            return;
        }
        if (!action.equals("set")) {
            plugin.send(player, "&7Benutzung: &e/plot flag set <Flag> <Wert>");
            return;
        }
        if (args.length < 3) {
            plugin.send(player, "&cBitte einen Wert angeben.");
            return;
        }
        String value;
        if (isBool) {
            String in = args[2].toLowerCase(Locale.ROOT);
            if (in.equals("true") || in.equals("ja") || in.equals("an") || in.equals("on")) {
                value = "true";
            } else if (in.equals("false") || in.equals("nein") || in.equals("aus") || in.equals("off")) {
                value = "false";
            } else {
                plugin.send(player, "&cDieses Flag braucht &etrue&c oder &efalse&c.");
                return;
            }
        } else {
            value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            if (value.length() > 80) {
                plugin.send(player, "&cDer Text ist zu lang (maximal 80 Zeichen).");
                return;
            }
        }
        for (PlotManager.Plot p : plots.group(plot)) {
            p.flags.put(name, value);
        }
        plots.save();
        plugin.send(player, "&7Flag &e" + name + "&7 ist jetzt &e" + value + "&7.");
    }

    // ---------- Biom ----------

    private void biome(Player player, String[] args) {
        if (args.length != 1) {
            plugin.send(player, "&7Benutzung: &e/plot biome <Biom> &7- Moeglich: &e"
                    + String.join("&7, &e", PlotManager.BIOMES.keySet()));
            return;
        }
        PlotManager.Plot plot = manageable(player);
        if (plot == null) {
            return;
        }
        Biome biome = PlotManager.BIOMES.get(args[0].toLowerCase(Locale.ROOT));
        if (biome == null) {
            plugin.send(player, "&cUnbekanntes Biom. Moeglich: &e" + String.join("&7, &e", PlotManager.BIOMES.keySet()));
            return;
        }
        plugin.send(player, "&7Biom wird geaendert ...");
        plugin.plots().setBiome(plugin.plots().groupRects(plot), biome,
                () -> plugin.send(player, "&aBiom geaendert. Verlasse die Welt kurz oder melde dich neu an, um es zu sehen."));
    }

    // ---------- Tab-Vervollstaendigung ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.addAll(SUBS);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "add", "remove", "deny", "undeny", "setowner", "visit", "trust", "untrust" -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        result.add(p.getName());
                    }
                }
                case "merge", "unmerge" -> result.addAll(List.of("norden", "osten", "sueden", "west"));
                case "flag" -> result.addAll(List.of("set", "remove", "list"));
                case "biome" -> result.addAll(PlotManager.BIOMES.keySet());
                case "clear", "delete" -> result.add("bestaetigen");
                default -> { }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("flag")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("remove"))) {
            result.addAll(PlotManager.BOOL_FLAGS);
            result.addAll(PlotManager.TEXT_FLAGS);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("flag") && args[1].equalsIgnoreCase("set")
                && PlotManager.BOOL_FLAGS.contains(args[2].toLowerCase(Locale.ROOT))) {
            result.addAll(List.of("true", "false"));
        }
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        result.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return result;
    }
}
