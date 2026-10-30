package de.citybuild;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SellCommand implements TabExecutor {

    private final CityBuildPlugin plugin;

    public SellCommand(CityBuildPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "&cDieser Befehl ist nur fuer Spieler.");
            return true;
        }
        if (args.length != 1) {
            plugin.send(player, "&7Benutzung: &e/sell hand &7| &e/sell all &7| &e/sell list");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "hand" -> sellHand(player);
            case "all", "alles" -> sellAll(player);
            case "list", "liste" -> list(player);
            default -> plugin.send(player, "&7Benutzung: &e/sell hand &7| &e/sell all &7| &e/sell list");
        }
        return true;
    }

    private double priceOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.hasItemMeta()) {
            return -1;
        }
        return plugin.getConfig().getDouble("sell.prices." + item.getType().name(), -1);
    }

    private void sellHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        double price = priceOf(item);
        if (price < 0) {
            plugin.send(player, "&cDieses Item kann nicht verkauft werden. Siehe &e/sell list&c.");
            return;
        }
        double total = price * item.getAmount();
        int amount = item.getAmount();
        Material type = item.getType();
        player.getInventory().setItemInMainHand(null);
        payout(player, total, amount + "x " + type.name());
    }

    private void sellAll(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        double total = 0;
        int count = 0;
        for (int i = 0; i < contents.length; i++) {
            double price = priceOf(contents[i]);
            if (price >= 0) {
                total += price * contents[i].getAmount();
                count += contents[i].getAmount();
                contents[i] = null;
            }
        }
        if (count == 0) {
            plugin.send(player, "&cDu hast nichts Verkaufbares im Inventar. Siehe &e/sell list&c.");
            return;
        }
        inv.setStorageContents(contents);
        payout(player, total, count + " Items");
    }

    private void payout(Player player, double total, String what) {
        EconomyManager economy = plugin.economy();
        economy.deposit(player.getUniqueId(), total);
        plugin.send(player, "&7Verkauft: &e" + what + "&7 fuer &a" + economy.format(total));
    }

    private void list(Player player) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("sell.prices");
        if (section == null || section.getKeys(false).isEmpty()) {
            plugin.send(player, "&7Es sind keine Verkaufspreise festgelegt.");
            return;
        }
        EconomyManager economy = plugin.economy();
        plugin.sendRaw(player, "&8&m      &r &bVerkaufspreise (pro Stueck) &8&m      ");
        for (String key : section.getKeys(false)) {
            plugin.sendRaw(player, "&7" + key.toLowerCase(Locale.ROOT).replace('_', ' ')
                    + " &8- &a" + economy.format(section.getDouble(key)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.addAll(List.of("hand", "all", "list"));
            result.removeIf(s -> !s.startsWith(args[0].toLowerCase(Locale.ROOT)));
        }
        return result;
    }
}
