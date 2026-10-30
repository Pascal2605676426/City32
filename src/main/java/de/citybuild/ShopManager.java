package de.citybuild;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Adminshop mit Menue: Kategorien, Kaufen und Verkaufen per Klick. */
public class ShopManager implements Listener {

    private static final int MAIN_SIZE = 27;
    private static final int CATEGORY_SIZE = 54;
    private static final int ITEM_SLOTS = 45;
    private static final int BACK_SLOT = 49;

    /** Kennzeichnet unsere Shop-Inventare. */
    public static class ShopHolder implements InventoryHolder {
        private final String category; // null = Hauptmenue
        private final List<String> categoryKeys = new ArrayList<>();
        private final List<Material> materials = new ArrayList<>();
        private Inventory inventory;

        ShopHolder(String category) {
            this.category = category;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final CityBuildPlugin plugin;

    public ShopManager(CityBuildPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- Menues ----------

    public void openMain(Player player) {
        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("shop.categories");
        if (categories == null || categories.getKeys(false).isEmpty()) {
            plugin.send(player, "&cDer Shop ist noch leer.");
            return;
        }
        ShopHolder holder = new ShopHolder(null);
        Inventory inv = Bukkit.createInventory(holder, MAIN_SIZE, plugin.color("&8Shop - Kategorien"));
        holder.inventory = inv;
        fill(inv, 0, MAIN_SIZE);

        int index = 0;
        for (String key : categories.getKeys(false)) {
            if (index >= 9) {
                break;
            }
            ConfigurationSection cat = categories.getConfigurationSection(key);
            if (cat == null) {
                continue;
            }
            Material icon = Material.matchMaterial(cat.getString("icon", "CHEST"));
            if (icon == null) {
                icon = Material.CHEST;
            }
            List<Component> lore = new ArrayList<>();
            lore.add(line("&7Klicke zum Oeffnen"));
            inv.setItem(9 + index, item(icon, cat.getString("name", key), lore));
            holder.categoryKeys.add(key);
            index++;
        }
        player.openInventory(inv);
    }

    private void openCategory(Player player, String key) {
        ConfigurationSection cat = plugin.getConfig().getConfigurationSection("shop.categories." + key);
        if (cat == null) {
            return;
        }
        ConfigurationSection items = cat.getConfigurationSection("items");
        ShopHolder holder = new ShopHolder(key);
        Inventory inv = Bukkit.createInventory(holder, CATEGORY_SIZE,
                plugin.color("&8Shop - " + stripColor(cat.getString("name", key))));
        holder.inventory = inv;
        fill(inv, ITEM_SLOTS, CATEGORY_SIZE);

        EconomyManager economy = plugin.economy();
        if (items != null) {
            int slot = 0;
            for (String matName : items.getKeys(false)) {
                if (slot >= ITEM_SLOTS) {
                    break;
                }
                Material mat = Material.matchMaterial(matName);
                if (mat == null || !mat.isItem()) {
                    plugin.getLogger().warning("Shop: unbekanntes Item '" + matName + "' in Kategorie " + key);
                    continue;
                }
                double buy = items.getDouble(matName + ".buy", -1);
                double sell = items.getDouble(matName + ".sell", -1);

                List<Component> lore = new ArrayList<>();
                lore.add(line(buy > 0 ? "&7Kaufen: &a" + economy.format(buy) : "&7Kaufen: &8nicht moeglich"));
                lore.add(line(sell > 0 ? "&7Verkaufen: &c" + economy.format(sell) : "&7Verkaufen: &8nicht moeglich"));
                lore.add(line(" "));
                lore.add(line("&eLinksklick&7: 1 kaufen"));
                lore.add(line("&eShift + Links&7: " + mat.getMaxStackSize() + " kaufen"));
                lore.add(line("&eRechtsklick&7: 1 verkaufen"));
                lore.add(line("&eShift + Rechts&7: alle verkaufen"));

                ItemStack stack = new ItemStack(mat);
                ItemMeta meta = stack.getItemMeta();
                meta.lore(lore);
                stack.setItemMeta(meta);
                inv.setItem(slot, stack);
                holder.materials.add(mat);
                slot++;
            }
        }
        inv.setItem(BACK_SLOT, item(Material.ARROW, "&cZurueck", List.of(line("&7Zurueck zu den Kategorien"))));
        player.openInventory(inv);
    }

    // ---------- Klicks ----------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        int slot = event.getSlot();

        if (holder.category == null) {
            int index = slot - 9;
            if (index >= 0 && index < holder.categoryKeys.size()) {
                openCategory(player, holder.categoryKeys.get(index));
            }
            return;
        }

        if (slot == BACK_SLOT) {
            openMain(player);
            return;
        }
        if (slot < 0 || slot >= holder.materials.size()) {
            return;
        }
        Material mat = holder.materials.get(slot);
        double buy = plugin.getConfig().getDouble("shop.categories." + holder.category + ".items." + mat.name() + ".buy", -1);
        double sell = plugin.getConfig().getDouble("shop.categories." + holder.category + ".items." + mat.name() + ".sell", -1);

        ClickType click = event.getClick();
        switch (click) {
            case LEFT -> buy(player, mat, buy, 1);
            case SHIFT_LEFT -> buy(player, mat, buy, mat.getMaxStackSize());
            case RIGHT -> sell(player, mat, sell, false);
            case SHIFT_RIGHT -> sell(player, mat, sell, true);
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }

    // ---------- Kaufen / Verkaufen ----------

    private void buy(Player player, Material mat, double unitPrice, int amount) {
        if (unitPrice <= 0) {
            plugin.send(player, "&cDieses Item kann man nicht kaufen.");
            return;
        }
        EconomyManager economy = plugin.economy();
        double total = unitPrice * amount;
        if (!economy.withdraw(player.getUniqueId(), total)) {
            plugin.send(player, "&cDas kostet &e" + economy.format(total) + "&c, du hast aber nur &e"
                    + economy.format(economy.get(player.getUniqueId())) + "&c.");
            return;
        }
        Map<Integer, ItemStack> left = player.getInventory().addItem(new ItemStack(mat, amount));
        int notAdded = 0;
        for (ItemStack rest : left.values()) {
            notAdded += rest.getAmount();
        }
        if (notAdded > 0) {
            economy.deposit(player.getUniqueId(), unitPrice * notAdded);
        }
        int given = amount - notAdded;
        if (given <= 0) {
            plugin.send(player, "&cDein Inventar ist voll.");
            return;
        }
        plugin.send(player, "&7Gekauft: &e" + given + "x " + niceName(mat) + "&7 fuer &a"
                + economy.format(unitPrice * given));
    }

    private void sell(Player player, Material mat, double unitPrice, boolean all) {
        if (unitPrice <= 0) {
            plugin.send(player, "&cDieses Item kann man hier nicht verkaufen.");
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();

        int available = 0;
        for (ItemStack stack : contents) {
            if (isSellable(stack, mat)) {
                available += stack.getAmount();
            }
        }
        if (available <= 0) {
            plugin.send(player, "&cDu hast kein &e" + niceName(mat) + "&c im Inventar.");
            return;
        }
        int toSell = all ? available : 1;
        int remaining = toSell;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (!isSellable(stack, mat)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            remaining -= take;
            if (take >= stack.getAmount()) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
        inv.setStorageContents(contents);

        EconomyManager economy = plugin.economy();
        double total = unitPrice * toSell;
        economy.deposit(player.getUniqueId(), total);
        plugin.send(player, "&7Verkauft: &e" + toSell + "x " + niceName(mat) + "&7 fuer &a" + economy.format(total));
    }

    private boolean isSellable(ItemStack stack, Material mat) {
        return stack != null && stack.getType() == mat && !stack.hasItemMeta();
    }

    // ---------- Hilfsmethoden ----------

    private Component line(String text) {
        return plugin.color(text).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(Material mat, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(name));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private void fill(Inventory inv, int from, int to) {
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", new ArrayList<>());
        for (int i = from; i < to; i++) {
            inv.setItem(i, pane);
        }
    }

    private String niceName(Material mat) {
        return mat.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String stripColor(String text) {
        return text.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }
}
