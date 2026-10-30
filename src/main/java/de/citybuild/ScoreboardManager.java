package de.citybuild;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Seitliche Anzeige (Sidebar) mit Rang, Geld, Clan, Grundstuecken und Online-Zahl. */
public class ScoreboardManager {

    private static final int MAX_LINES = 12;

    private final CityBuildPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public ScoreboardManager(CityBuildPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateAll, 40L, 40L);
    }

    private static String entry(int index) {
        // Unsichtbarer, eindeutiger Eintrag pro Zeile
        return "§" + Integer.toHexString(index) + "§r";
    }

    public void add(Player player) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("citybuild", Criteria.DUMMY,
                plugin.color(plugin.getConfig().getString("scoreboard.title", "&b&lCityBuild")));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < MAX_LINES; i++) {
            Team team = board.registerNewTeam("l" + i);
            team.addEntry(entry(i));
        }
        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        update(player);
    }

    public void remove(Player player) {
        boards.remove(player.getUniqueId());
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
            plugin.refreshPlayer(player);
        }
    }

    private void update(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        Objective objective = board.getObjective("citybuild");
        if (objective == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        RankManager ranks = plugin.ranks();
        String rankKey = ranks.getRankKey(uuid);
        ClanManager.Clan clan = plugin.clans().getClan(uuid);
        int plotCount = plugin.plots().getOwned(uuid).size();
        int plotMax = plugin.getConfig().getInt("plots.max-per-player", 5);

        List<String> lines = new ArrayList<>();
        lines.add("&8&m                    ");
        lines.add("&7Name: &f" + player.getName());
        lines.add("&7Rang: " + ranks.nameColor(rankKey) + ranks.name(rankKey));
        lines.add("&7Geld: &a" + plugin.economy().format(plugin.economy().get(uuid)));
        lines.add("&7Clan: &f" + (clan == null ? "-" : clan.tag()));
        if (plugin.getConfig().getBoolean("plots.enabled", true)) {
            lines.add("&7Grundstuecke: &f" + plotCount + "&7/&f" + plotMax);
        }
        lines.add(" ");
        lines.add("&7Online: &f" + Bukkit.getOnlinePlayers().size());
        lines.add("  ");
        lines.add(plugin.getConfig().getString("scoreboard.footer", "&bplay.tickline.de"));

        for (int i = 0; i < MAX_LINES; i++) {
            Team team = board.getTeam("l" + i);
            if (team == null) {
                continue;
            }
            if (i < lines.size()) {
                team.prefix(plugin.color(lines.get(i)));
                objective.getScore(entry(i)).setScore(lines.size() - i);
            } else {
                board.resetScores(entry(i));
            }
        }
    }
}
