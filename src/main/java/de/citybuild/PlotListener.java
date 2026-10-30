package de.citybuild;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Schutz der Plot-Welt: Bauen, Interaktion, Kampf, Feuer, Explosionen, Mobs, Betreten. */
public class PlotListener implements Listener {

    private final CityBuildPlugin plugin;
    private final PlotManager plots;
    private final Map<UUID, Long> lastDeny = new HashMap<>();

    public PlotListener(CityBuildPlugin plugin) {
        this.plugin = plugin;
        this.plots = plugin.plots();
    }

    private void warn(Player player, String message) {
        long now = System.currentTimeMillis();
        Long last = lastDeny.get(player.getUniqueId());
        if (last != null && now - last < 1500) {
            return;
        }
        lastDeny.put(player.getUniqueId(), now);
        plugin.send(player, message);
    }

    private void deny(Player player) {
        warn(player, "&cHier darfst du nicht bauen. Das ist nicht dein Grundstueck (&e/plot claim&c).");
    }

    private String nameOf(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    // ---------- Bauen / Abbauen ----------

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!plots.canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!plots.canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!plots.canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!plots.canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    // ---------- Interaktion ----------

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Block block = e.getClickedBlock();
        if (block == null || !plots.isPlotWorld(block.getWorld())) {
            return;
        }
        Player player = e.getPlayer();

        if (e.getAction() == Action.PHYSICAL) {
            Material type = block.getType();
            if ((type == Material.FARMLAND || type == Material.TURTLE_EGG)
                    && !plots.canBuild(player, block.getLocation())) {
                e.setCancelled(true);
            }
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (plots.canBuild(player, block.getLocation())) {
            return;
        }

        PlotManager.Plot plot = plots.plotAt(block.getLocation());
        boolean onPlot = plot != null || plots.idAt(block.getLocation()) != null;

        // Besucher duerfen Tueren, Knoepfe usw. benutzen, wenn das Flag "visitor-use" gesetzt ist
        if (plot != null && plots.flagBool(plot, "visitor-use") && isUsable(block) && !isContainer(block)
                && !isPlacingItem(e.getItem())) {
            return;
        }
        if (isPlacingItem(e.getItem()) || isContainer(block) || (onPlot && isUsable(block))) {
            e.setCancelled(true);
            deny(player);
        }
    }

    private boolean isContainer(Block block) {
        return block.getState(false) instanceof Container;
    }

    private boolean isUsable(Block block) {
        Material type = block.getType();
        return block.getBlockData() instanceof Openable
                || Tag.BUTTONS.isTagged(type)
                || Tag.ANVIL.isTagged(type)
                || type == Material.LEVER
                || type == Material.COMPARATOR
                || type == Material.REPEATER
                || type == Material.DAYLIGHT_DETECTOR
                || type == Material.NOTE_BLOCK
                || type == Material.JUKEBOX
                || type == Material.LECTERN
                || type == Material.FLOWER_POT
                || type == Material.CAKE;
    }

    private boolean isPlacingItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        String name = type.name();
        return name.endsWith("_BUCKET")
                || name.endsWith("_SPAWN_EGG")
                || type == Material.FLINT_AND_STEEL
                || type == Material.FIRE_CHARGE
                || type == Material.ARMOR_STAND
                || type == Material.ITEM_FRAME
                || type == Material.GLOW_ITEM_FRAME
                || type == Material.PAINTING
                || type == Material.END_CRYSTAL;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity entity = e.getRightClicked();
        if (!(entity instanceof Hanging) && !(entity instanceof ArmorStand)) {
            return;
        }
        if (!plots.canBuild(e.getPlayer(), entity.getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (!plots.canBuild(e.getPlayer(), e.getRightClicked().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Player remover = null;
        if (e.getRemover() instanceof Player p1) {
            remover = p1;
        } else if (e.getRemover() instanceof Projectile proj && proj.getShooter() instanceof Player p2) {
            remover = p2;
        }
        if (remover != null && !plots.canBuild(remover, e.getEntity().getLocation())) {
            e.setCancelled(true);
            deny(remover);
        }
    }

    // ---------- Kampf ----------

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (!plots.isPlotWorld(victim.getWorld())) {
            return;
        }
        Player attacker = null;
        if (e.getDamager() instanceof Player p1) {
            attacker = p1;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p2) {
            attacker = p2;
        }
        if (attacker == null) {
            return;
        }

        if (victim instanceof Player) {
            PlotManager.Plot plot = plots.plotAt(victim.getLocation());
            if (!plots.flagBool(plot, "pvp")) {
                e.setCancelled(true);
            }
            return;
        }
        if (victim instanceof Enemy) {
            return;
        }
        if (!plots.canBuild(attacker, victim.getLocation())) {
            e.setCancelled(true);
            deny(attacker);
        }
    }

    // ---------- Betreten, Verlassen, Deny ----------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (!plots.isPlotWorld(to.getWorld())) {
            return;
        }
        Player player = e.getPlayer();
        PlotManager.Plot a = plots.plotAt(from);
        PlotManager.Plot b = plots.plotAt(to);
        if (a == b) {
            return;
        }

        if (b != null && b.denied.contains(player.getUniqueId()) && !b.isMember(player.getUniqueId())
                && !player.hasPermission("citybuild.bypass")) {
            e.setCancelled(true);
            warn(player, "&cDu darfst dieses Grundstueck nicht betreten.");
            return;
        }

        if (a != null) {
            String farewell = plots.flagText(a, "farewell");
            if (farewell != null && !farewell.isEmpty()) {
                player.sendActionBar(plugin.color(farewell));
            }
        }
        if (b != null) {
            String greeting = plots.flagText(b, "greeting");
            if (greeting != null && !greeting.isEmpty()) {
                player.sendActionBar(plugin.color(greeting));
            } else {
                player.sendActionBar(plugin.color("&7Grundstueck von &e" + nameOf(b.owner)));
            }
        }
    }

    // ---------- Mobs ----------

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        if (!plots.isPlotWorld(e.getLocation().getWorld())) {
            return;
        }
        PlotManager.Plot plot = plots.plotAt(e.getLocation());
        if (plot == null || !plots.flagBool(plot, "mob-spawning")) {
            e.setCancelled(true);
        }
    }

    // ---------- Explosionen, Feuer, Fluessigkeiten, Kolben ----------

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (plots.isPlotWorld(e.getLocation().getWorld())) {
            e.blockList().removeIf(block -> !plots.flagBool(plots.plotAt(block.getLocation()), "explosion")
                    || plots.plotAt(block.getLocation()) == null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (plots.isPlotWorld(e.getBlock().getWorld())) {
            e.blockList().removeIf(block -> !plots.flagBool(plots.plotAt(block.getLocation()), "explosion")
                    || plots.plotAt(block.getLocation()) == null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!plots.isPlotWorld(e.getBlock().getWorld())) {
            return;
        }
        BlockIgniteEvent.IgniteCause cause = e.getCause();
        if (cause == BlockIgniteEvent.IgniteCause.SPREAD
                || cause == BlockIgniteEvent.IgniteCause.LAVA
                || cause == BlockIgniteEvent.IgniteCause.LIGHTNING) {
            PlotManager.Plot plot = plots.plotAt(e.getBlock().getLocation());
            if (plot == null || !plots.flagBool(plot, "fire")) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (!plots.isPlotWorld(e.getBlock().getWorld())) {
            return;
        }
        PlotManager.Plot plot = plots.plotAt(e.getBlock().getLocation());
        if (plot == null || !plots.flagBool(plot, "fire")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Block from = e.getBlock();
        Block to = e.getToBlock();
        if ((from.getX() >> 4) == (to.getX() >> 4) && (from.getZ() >> 4) == (to.getZ() >> 4)) {
            return;
        }
        if (!plots.isPlotWorld(from.getWorld())) {
            return;
        }
        // Wasser und Lava duerfen nicht von einem Grundstueck ins andere fliessen
        if (!plots.samePlotOrOwner(plots.plotAt(from.getLocation()), plots.plotAt(to.getLocation()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (isCrossingPlots(e.getBlock(), e.getBlocks(), e.getDirection())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (isCrossingPlots(e.getBlock(), e.getBlocks(), e.getDirection())) {
            e.setCancelled(true);
        }
    }

    private boolean isCrossingPlots(Block piston, List<Block> moved, BlockFace direction) {
        if (!plots.isPlotWorld(piston.getWorld())) {
            return false;
        }
        PlotManager.Plot home = plots.plotAt(piston.getLocation());
        if (home == null) {
            return true;
        }
        if (!plots.samePlotOrOwner(home, plots.plotAt(piston.getRelative(direction).getLocation()))) {
            return true;
        }
        for (Block block : moved) {
            if (!plots.samePlotOrOwner(home, plots.plotAt(block.getLocation()))
                    || !plots.samePlotOrOwner(home, plots.plotAt(block.getRelative(direction).getLocation()))) {
                return true;
            }
        }
        return false;
    }
}
