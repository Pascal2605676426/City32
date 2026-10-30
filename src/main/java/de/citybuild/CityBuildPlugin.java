package de.citybuild;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class CityBuildPlugin extends JavaPlugin implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private EconomyManager economy;
    private PlotManager plots;
    private WarpManager warps;
    private HomeManager homes;
    private TeleportManager teleports;
    private RankManager ranks;
    private ClanManager clans;
    private ShopManager shop;
    private DailyManager daily;
    private WorldManager worlds;
    private ScoreboardManager scoreboards;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        economy = new EconomyManager(this);
        plots = new PlotManager(this);
        warps = new WarpManager(this);
        homes = new HomeManager(this);
        teleports = new TeleportManager(this);
        ranks = new RankManager(this);
        clans = new ClanManager(this);
        shop = new ShopManager(this);
        daily = new DailyManager(this);
        worlds = new WorldManager(this);
        scoreboards = new ScoreboardManager(this);

        getServer().getPluginManager().registerEvents(this, this);
        if (getConfig().getBoolean("plots.enabled", true)) {
            plots.loadWorld();
            getServer().getPluginManager().registerEvents(new PlotListener(this), this);
            register("plot", new PlotCommand(this));
        }
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(shop, this);

        EconomyCommands eco = new EconomyCommands(this);
        register("money", eco);
        register("pay", eco);
        register("baltop", eco);
        register("eco", eco);

        HomeCommands homeCommands = new HomeCommands(this);
        register("sethome", homeCommands);
        register("home", homeCommands);
        register("delhome", homeCommands);
        register("homes", homeCommands);

        WarpCommands warpCommands = new WarpCommands(this);
        register("spawn", warpCommands);
        register("setspawn", warpCommands);
        register("warp", warpCommands);
        register("warps", warpCommands);
        register("setwarp", warpCommands);
        register("delwarp", warpCommands);

        register("sell", new SellCommand(this));
        register("shop", new ShopCommand(this));
        register("daily", new DailyCommand(this));
        register("rang", new RankCommand(this));

        WorldCommands worldCommands = new WorldCommands(this);
        register("farmwelt", worldCommands);
        register("nether", worldCommands);
        register("end", worldCommands);

        ClanCommand clanCommand = new ClanCommand(this);
        register("clan", clanCommand);
        register("cc", clanCommand);

        // Alle 5 Minuten Kontostaende speichern
        getServer().getScheduler().runTaskTimer(this, () -> economy.save(), 6000L, 6000L);

        // Falls das Plugin per Reload aktiviert wird: Spieler, die schon online sind, einrichten
        for (Player player : getServer().getOnlinePlayers()) {
            setupPlayer(player);
        }

        getLogger().info("CityBuild aktiviert.");
    }

    @Override
    public void onDisable() {
        if (economy != null) {
            economy.save();
        }
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("citybuild")) {
            if (!sender.hasPermission("citybuild.admin")) {
                send(sender, "&cDazu hast du keine Berechtigung.");
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                send(sender, "&aKonfiguration neu geladen.");
            } else {
                send(sender, "&7Benutzung: &e/citybuild reload");
            }
            return true;
        }
        return false;
    }

    // ---------- Hilfsmethoden ----------

    public Component color(String text) {
        return LEGACY.deserialize(text);
    }

    /** Nachricht mit Prefix senden. */
    public void send(CommandSender target, String message) {
        target.sendMessage(color(getConfig().getString("prefix", "&8[&bCityBuild&8] &7") + message));
    }

    /** Nachricht ohne Prefix senden. */
    public void sendRaw(CommandSender target, String message) {
        target.sendMessage(color(message));
    }

    /** Name mit Rang-Prefix und Clan-Tag, z.B. "Admin | [ABC] Pascal". */
    public Component formatName(Player player) {
        UUID uuid = player.getUniqueId();
        String rankKey = ranks.getRankKey(uuid);
        ClanManager.Clan clan = clans.getClan(uuid);
        String clanPart = clan == null ? "" : "&8[&b" + clan.tag() + "&8] ";
        return color(ranks.prefix(rankKey) + clanPart + ranks.nameColor(rankKey) + player.getName());
    }

    /** Aktualisiert Tab-Liste (Name, Kopf- und Fusszeile) eines Spielers. */
    public void refreshPlayer(Player player) {
        player.playerListName(formatName(player));
        player.sendPlayerListHeaderAndFooter(
                color(getConfig().getString("tablist.header", "&b&lCityBuild")),
                color(getConfig().getString("tablist.footer", "&7play.tickline.de")));
    }

    private void setupPlayer(Player player) {
        economy.ensureAccount(player.getUniqueId());
        scoreboards.add(player);
        refreshPlayer(player);
    }

    // ---------- Events ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        setupPlayer(player);

        event.joinMessage(color("&8[&a+&8] &7" + player.getName()));

        if (!player.hasPlayedBefore() && getConfig().getBoolean("spawn.teleport-on-first-join", true)) {
            Location spawn = warps.getSpawn();
            if (spawn != null) {
                getServer().getScheduler().runTaskLater(this, () -> player.teleportAsync(spawn), 2L);
            }
        }

        if (daily.remaining(player.getUniqueId()) == 0L) {
            getServer().getScheduler().runTaskLater(this,
                    () -> send(player, "&aDeine taegliche Belohnung wartet! Hole sie mit &e/daily&a ab."), 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        scoreboards.remove(event.getPlayer());
        event.quitMessage(color("&8[&c-&8] &7" + event.getPlayer().getName()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!getConfig().getBoolean("spawn.respawn-at-spawn", true)) {
            return;
        }
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        Location spawn = warps.getSpawn();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    // ---------- Zugriff ----------

    public EconomyManager economy() {
        return economy;
    }

    public PlotManager plots() {
        return plots;
    }

    public WarpManager warps() {
        return warps;
    }

    public HomeManager homes() {
        return homes;
    }

    public TeleportManager teleports() {
        return teleports;
    }

    public RankManager ranks() {
        return ranks;
    }

    public ClanManager clans() {
        return clans;
    }

    public ShopManager shop() {
        return shop;
    }

    public DailyManager daily() {
        return daily;
    }

    public WorldManager worlds() {
        return worlds;
    }
}
