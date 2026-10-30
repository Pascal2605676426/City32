package de.citybuild;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** /daily - taegliche Belohnung abholen. */
public class DailyCommand implements TabExecutor {

    private final CityBuildPlugin plugin;

    public DailyCommand(CityBuildPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "&cDieser Befehl ist nur fuer Spieler.");
            return true;
        }
        UUID uuid = player.getUniqueId();
        DailyManager daily = plugin.daily();

        long remaining = daily.remaining(uuid);
        if (remaining > 0) {
            plugin.send(player, "&cDu hast deine Belohnung schon abgeholt. Wieder moeglich in &e"
                    + DailyManager.formatDuration(remaining) + "&c.");
            return true;
        }
        int streak = daily.claim(uuid);
        double reward = daily.rewardFor(streak);
        plugin.economy().ensureAccount(uuid);
        plugin.economy().deposit(uuid, reward);
        plugin.send(player, "&aTaegliche Belohnung: &e" + plugin.economy().format(reward)
                + " &7(Serie: &e" + streak + " &7Tag" + (streak == 1 ? "" : "e") + ")");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
