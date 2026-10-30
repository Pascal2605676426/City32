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
import java.util.Map;
import java.util.UUID;

public class EconomyCommands implements TabExecutor {

    private final CityBuildPlugin plugin;

    public EconomyCommands(CityBuildPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "money" -> money(sender, args);
            case "pay" -> pay(sender, args);
            case "baltop" -> baltop(sender);
            case "eco" -> eco(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void money(CommandSender sender, String[] args) {
        EconomyManager economy = plugin.economy();
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.send(sender, "&cNur Spieler haben ein Konto. Nutze /money <Spieler>.");
                return;
            }
            plugin.send(player, "&7Dein Guthaben: &a" + economy.format(economy.get(player.getUniqueId())));
            return;
        }
        OfflinePlayer target = resolve(args[0]);
        if (target == null || !economy.hasAccount(target.getUniqueId())) {
            plugin.send(sender, "&cDieser Spieler hat kein Konto.");
            return;
        }
        plugin.send(sender, "&e" + args[0] + "&7 hat &a" + economy.format(economy.get(target.getUniqueId())));
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "&cDieser Befehl ist nur fuer Spieler.");
            return;
        }
        if (args.length != 2) {
            plugin.send(player, "&7Benutzung: &e/pay <Spieler> <Betrag>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.send(player, "&cSpieler nicht gefunden (muss online sein).");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.send(player, "&cDu kannst dir nicht selbst Geld senden.");
            return;
        }
        Double amount = parseAmount(args[1]);
        double max = plugin.getConfig().getDouble("economy.max-pay", 1_000_000_000.0);
        if (amount == null || amount < 0.01 || amount > max) {
            plugin.send(player, "&cUngueltiger Betrag.");
            return;
        }
        EconomyManager economy = plugin.economy();
        if (!economy.withdraw(player.getUniqueId(), amount)) {
            plugin.send(player, "&cDu hast nicht genug Geld.");
            return;
        }
        economy.deposit(target.getUniqueId(), amount);
        plugin.send(player, "&7Du hast &e" + target.getName() + " &a" + economy.format(amount) + "&7 gesendet.");
        plugin.send(target, "&e" + player.getName() + "&7 hat dir &a" + economy.format(amount) + "&7 gesendet.");
    }

    private void baltop(CommandSender sender) {
        EconomyManager economy = plugin.economy();
        plugin.sendRaw(sender, "&8&m      &r &bReichste Spieler &8&m      ");
        int rank = 1;
        for (Map.Entry<UUID, Double> entry : economy.top(10)) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
            String name = op.getName() != null ? op.getName() : entry.getKey().toString().substring(0, 8);
            plugin.sendRaw(sender, "&e" + rank + ". &f" + name + " &8- &a" + economy.format(entry.getValue()));
            rank++;
        }
    }

    private void eco(CommandSender sender, String[] args) {
        if (!sender.hasPermission("citybuild.eco")) {
            plugin.send(sender, "&cDazu hast du keine Berechtigung.");
            return;
        }
        if (args.length != 3) {
            plugin.send(sender, "&7Benutzung: &e/eco <give|take|set> <Spieler> <Betrag>");
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            plugin.send(sender, "&cSpieler nicht gefunden.");
            return;
        }
        Double amount = parseAmount(args[2]);
        if (amount == null || amount < 0) {
            plugin.send(sender, "&cUngueltiger Betrag.");
            return;
        }
        EconomyManager economy = plugin.economy();
        UUID uuid = target.getUniqueId();
        economy.ensureAccount(uuid);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> economy.deposit(uuid, amount);
            case "take" -> economy.set(uuid, economy.get(uuid) - amount);
            case "set" -> economy.set(uuid, amount);
            default -> {
                plugin.send(sender, "&7Benutzung: &e/eco <give|take|set> <Spieler> <Betrag>");
                return;
            }
        }
        plugin.send(sender, "&7Neues Guthaben von &e" + args[1] + "&7: &a" + economy.format(economy.get(uuid)));
    }

    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private Double parseAmount(String input) {
        try {
            double value = Double.parseDouble(input.replace(',', '.'));
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> result = new ArrayList<>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("eco")) {
            if (args.length == 1) {
                result.addAll(List.of("give", "take", "set"));
            } else if (args.length == 2) {
                addPlayers(result);
            }
        } else if ((name.equals("money") || name.equals("pay")) && args.length == 1) {
            addPlayers(result);
        }
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        result.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return result;
    }

    private void addPlayers(List<String> list) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            list.add(p.getName());
        }
    }
}
