package de.citybuild;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Verwaltet alle Grundstuecke der Plot-Welt: Gitter-Geometrie, Besitzer, Flags,
 * Verbinden (merge), Leeren und Biome.
 */
public class PlotManager {

    public static final List<String> BOOL_FLAGS = List.of("pvp", "mob-spawning", "fire", "explosion", "visitor-use");
    public static final List<String> TEXT_FLAGS = List.of("greeting", "farewell");

    public static final Map<String, Biome> BIOMES = new LinkedHashMap<>();

    static {
        BIOMES.put("plains", Biome.PLAINS);
        BIOMES.put("forest", Biome.FOREST);
        BIOMES.put("birch_forest", Biome.BIRCH_FOREST);
        BIOMES.put("dark_forest", Biome.DARK_FOREST);
        BIOMES.put("taiga", Biome.TAIGA);
        BIOMES.put("snowy_plains", Biome.SNOWY_PLAINS);
        BIOMES.put("desert", Biome.DESERT);
        BIOMES.put("savanna", Biome.SAVANNA);
        BIOMES.put("badlands", Biome.BADLANDS);
        BIOMES.put("jungle", Biome.JUNGLE);
        BIOMES.put("swamp", Biome.SWAMP);
        BIOMES.put("meadow", Biome.MEADOW);
        BIOMES.put("cherry_grove", Biome.CHERRY_GROVE);
        BIOMES.put("mushroom_fields", Biome.MUSHROOM_FIELDS);
        BIOMES.put("ocean", Biome.OCEAN);
    }

    /** Ein Grundstueck. x und z sind Nummern im Gitter, keine Blockkoordinaten. */
    public static class Plot {
        public final int x;
        public final int z;
        public UUID owner;
        public final long created;
        public final Set<UUID> trusted = new LinkedHashSet<>();
        public final Set<UUID> denied = new LinkedHashSet<>();
        public final Map<String, String> flags = new LinkedHashMap<>();
        /** Verbunden mit dem Grundstueck rechts (x + 1). */
        public boolean mergeEast;
        /** Verbunden mit dem Grundstueck dahinter (z + 1). */
        public boolean mergeSouth;

        public Plot(int x, int z, UUID owner, long created) {
            this.x = x;
            this.z = z;
            this.owner = owner;
            this.created = created;
        }

        public boolean isMember(UUID uuid) {
            return owner.equals(uuid) || trusted.contains(uuid);
        }
    }

    private static final int K_PLOT = 0;
    private static final int K_WALL_UNCLAIMED = 1;
    private static final int K_WALL_CLAIMED = 2;
    private static final int K_ROAD = 3;

    private static final int COLUMNS_PER_TICK = 48;

    private final CityBuildPlugin plugin;
    private final PlotLayout layout;
    private final File file;
    private final Map<Long, Plot> plots = new HashMap<>();
    private World world;

    public PlotManager(CityBuildPlugin plugin) {
        this.plugin = plugin;
        this.layout = new PlotLayout(plugin.getConfig());
        this.file = new File(plugin.getDataFolder(), "plots.yml");
        load();
    }

    public PlotLayout layout() {
        return layout;
    }

    // ---------- Welt ----------

    public void loadWorld() {
        String name = plugin.getConfig().getString("plots.world", "plotwelt");
        world = Bukkit.getWorld(name);
        if (world == null) {
            try {
                world = WorldCreator.name(name)
                        .generator(new PlotGenerator(layout))
                        .biomeProvider(new PlotBiomeProvider())
                        .environment(World.Environment.NORMAL)
                        .createWorld();
            } catch (Exception e) {
                plugin.getLogger().severe("Plot-Welt konnte nicht erstellt werden: " + e.getMessage());
            }
        }
        if (world == null) {
            plugin.getLogger().severe("Die Plot-Welt '" + name + "' ist nicht verfuegbar.");
        }
    }

    public World world() {
        return world;
    }

    public boolean isPlotWorld(World w) {
        return w != null && world != null && w.getName().equals(world.getName());
    }

    // ---------- Geometrie ----------

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public Plot get(int x, int z) {
        return plots.get(key(x, z));
    }

    /** Ist die Blockposition Teil einer Grundstuecksflaeche (inklusive verbundener Strassen)? */
    private boolean occupied(int wx, int wz) {
        boolean px = layout.isPlotCoord(wx);
        boolean pz = layout.isPlotCoord(wz);
        int cx = layout.cellOf(wx);
        int cz = layout.cellOf(wz);
        if (px && pz) {
            return true;
        }
        if (px) {
            Plot n = get(cx, cz - 1);
            return n != null && n.mergeSouth;
        }
        if (pz) {
            Plot w = get(cx - 1, cz);
            return w != null && w.mergeEast;
        }
        Plot a = get(cx - 1, cz - 1);
        Plot b = get(cx, cz - 1);
        Plot c = get(cx - 1, cz);
        Plot d = get(cx, cz);
        return a != null && b != null && c != null && d != null
                && a.mergeEast && a.mergeSouth && b.mergeSouth && c.mergeEast;
    }

    /** Das Grundstueck an dieser Blockposition (auch verbundene Strassen), sonst null. */
    public Plot plotAt(int wx, int wz) {
        boolean px = layout.isPlotCoord(wx);
        boolean pz = layout.isPlotCoord(wz);
        int cx = layout.cellOf(wx);
        int cz = layout.cellOf(wz);
        if (px && pz) {
            return get(cx, cz);
        }
        if (!occupied(wx, wz)) {
            return null;
        }
        if (px) {
            return get(cx, cz - 1);
        }
        if (pz) {
            return get(cx - 1, cz);
        }
        return get(cx - 1, cz - 1);
    }

    public Plot plotAt(Location loc) {
        if (loc.getWorld() == null || !isPlotWorld(loc.getWorld())) {
            return null;
        }
        return plotAt(loc.getBlockX(), loc.getBlockZ());
    }

    /** Gitter-Nummer {x, z}, wenn die Position auf einem Grundstueck (auch freiem) liegt, sonst null. */
    public int[] idAt(Location loc) {
        if (loc.getWorld() == null || !isPlotWorld(loc.getWorld())) {
            return null;
        }
        int bx = loc.getBlockX();
        int bz = loc.getBlockZ();
        if (!layout.isPlotCoord(bx) || !layout.isPlotCoord(bz)) {
            return null;
        }
        return new int[]{layout.cellOf(bx), layout.cellOf(bz)};
    }

    public boolean samePlotOrOwner(Plot a, Plot b) {
        if (a == b) {
            return true;
        }
        return a != null && b != null && a.owner.equals(b.owner);
    }

    // ---------- Besitz ----------

    public List<Plot> getOwned(UUID owner) {
        List<Plot> list = new ArrayList<>();
        for (Plot plot : plots.values()) {
            if (plot.owner.equals(owner)) {
                list.add(plot);
            }
        }
        list.sort((a, b) -> Long.compare(a.created, b.created));
        return list;
    }

    public Plot claim(int x, int z, UUID owner) {
        Plot plot = new Plot(x, z, owner, System.currentTimeMillis());
        plots.put(key(x, z), plot);
        save();
        redrawIds(x, z);
        return plot;
    }

    public boolean hasLinks(Plot p) {
        Plot w = get(p.x - 1, p.z);
        Plot n = get(p.x, p.z - 1);
        return p.mergeEast || p.mergeSouth || (w != null && w.mergeEast) || (n != null && n.mergeSouth);
    }

    /** Entfernt den Besitzer (Grundstueck wird wieder frei). Verbindungen muessen vorher getrennt sein. */
    public void remove(Plot p) {
        plots.remove(key(p.x, p.z));
        save();
        redrawIds(p.x, p.z);
    }

    public Set<Plot> group(Plot start) {
        Set<Plot> seen = new LinkedHashSet<>();
        Deque<Plot> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            Plot p = queue.poll();
            List<Plot> next = new ArrayList<>();
            if (p.mergeEast) {
                next.add(get(p.x + 1, p.z));
            }
            Plot w = get(p.x - 1, p.z);
            if (w != null && w.mergeEast) {
                next.add(w);
            }
            if (p.mergeSouth) {
                next.add(get(p.x, p.z + 1));
            }
            Plot n = get(p.x, p.z - 1);
            if (n != null && n.mergeSouth) {
                next.add(n);
            }
            for (Plot q : next) {
                if (q != null && seen.add(q)) {
                    queue.add(q);
                }
            }
        }
        return seen;
    }

    public void setOwner(Plot p, UUID newOwner) {
        for (Plot q : group(p)) {
            q.owner = newOwner;
            q.trusted.remove(newOwner);
            q.denied.remove(newOwner);
        }
        save();
        redrawIds(p.x, p.z);
    }

    // ---------- Rechte und Flags ----------

    public boolean canBuild(Player player, Location loc) {
        if (player.hasPermission("citybuild.bypass")) {
            return true;
        }
        if (loc.getWorld() == null || !isPlotWorld(loc.getWorld())) {
            return true;
        }
        Plot plot = plotAt(loc);
        return plot != null && plot.isMember(player.getUniqueId());
    }

    public boolean flagBool(Plot plot, String name) {
        String value = plot == null ? null : plot.flags.get(name);
        if (value == null) {
            value = plugin.getConfig().getString("plots.default-flags." + name, "false");
        }
        return Boolean.parseBoolean(value);
    }

    public String flagText(Plot plot, String name) {
        return plot == null ? null : plot.flags.get(name);
    }

    // ---------- Orte ----------

    public Location home(Plot p) {
        double x = (layout.min(p.x) + layout.max(p.x)) / 2.0 + 0.5;
        double z = layout.min(p.z) - 1 - layout.road / 2 + 0.5;
        return new Location(world, x, layout.height + 1, z, 0f, 0f);
    }

    public Location worldSpawn() {
        double x = layout.min(0) + layout.size / 2.0;
        double z = layout.road / 2.0;
        return new Location(world, x, layout.height + 1, z, 0f, 0f);
    }

    // ---------- Verbinden ----------

    /** dir: 0 = Norden (-z), 1 = Osten (+x), 2 = Sueden (+z), 3 = Westen (-x). Gibt eine Fehlermeldung oder null zurueck. */
    public String merge(Plot a, int dir) {
        Plot b = neighbor(a, dir);
        if (b == null) {
            return "Dort liegt kein vergebenes Grundstueck.";
        }
        if (!b.owner.equals(a.owner)) {
            return "Das Nachbar-Grundstueck gehoert jemand anderem.";
        }
        if (isLinked(a, b, dir)) {
            return "Die beiden Grundstuecke sind schon verbunden.";
        }
        setLink(a, b, dir, true);

        Set<UUID> trusted = new HashSet<>();
        Set<UUID> denied = new HashSet<>();
        Set<Plot> group = group(a);
        for (Plot p : group) {
            trusted.addAll(p.trusted);
            denied.addAll(p.denied);
        }
        for (Plot p : group) {
            p.trusted.addAll(trusted);
            p.denied.addAll(denied);
            if (p != a) {
                p.flags.clear();
                p.flags.putAll(a.flags);
            }
        }
        save();
        redrawIds(a.x, a.z);
        redrawIds(b.x, b.z);
        return null;
    }

    public String unmerge(Plot a, int dir) {
        Plot b = neighbor(a, dir);
        if (b == null || !isLinked(a, b, dir)) {
            return "In dieser Richtung ist nichts verbunden.";
        }
        setLink(a, b, dir, false);
        save();
        redrawIds(a.x, a.z);
        redrawIds(b.x, b.z);
        return null;
    }

    private Plot neighbor(Plot a, int dir) {
        return switch (dir) {
            case 0 -> get(a.x, a.z - 1);
            case 1 -> get(a.x + 1, a.z);
            case 2 -> get(a.x, a.z + 1);
            default -> get(a.x - 1, a.z);
        };
    }

    private boolean isLinked(Plot a, Plot b, int dir) {
        return switch (dir) {
            case 0 -> b.mergeSouth;
            case 1 -> a.mergeEast;
            case 2 -> a.mergeSouth;
            default -> b.mergeEast;
        };
    }

    private void setLink(Plot a, Plot b, int dir, boolean value) {
        switch (dir) {
            case 0 -> b.mergeSouth = value;
            case 1 -> a.mergeEast = value;
            case 2 -> a.mergeSouth = value;
            default -> b.mergeEast = value;
        }
    }

    // ---------- Zeichnen (Rand, Strassen) ----------

    private int kind(int wx, int wz) {
        if (occupied(wx, wz)) {
            return K_PLOT;
        }
        boolean near = false;
        boolean claimed = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (occupied(wx + dx, wz + dz)) {
                    near = true;
                    if (plotAt(wx + dx, wz + dz) != null) {
                        claimed = true;
                    }
                }
            }
        }
        if (!near) {
            return K_ROAD;
        }
        return claimed ? K_WALL_CLAIMED : K_WALL_UNCLAIMED;
    }

    private void redrawIds(int x, int z) {
        redraw(layout.min(x) - layout.road, layout.max(x) + layout.road,
                layout.min(z) - layout.road, layout.max(z) + layout.road);
    }

    private void redraw(int minX, int maxX, int minZ, int maxZ) {
        if (world == null) {
            return;
        }
        int ph = layout.height;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (layout.isPlotCoord(x) && layout.isPlotCoord(z)) {
                    continue;
                }
                switch (kind(x, z)) {
                    case K_PLOT -> column(x, z, layout.filler, layout.floor, null);
                    case K_WALL_CLAIMED -> column(x, z, layout.stone, layout.roadMat, layout.wallClaimed);
                    case K_WALL_UNCLAIMED -> column(x, z, layout.stone, layout.roadMat, layout.wallUnclaimed);
                    default -> column(x, z, layout.stone, layout.roadMat, null);
                }
            }
        }
    }

    private void column(int x, int z, Material below, Material top, Material wall) {
        int ph = layout.height;
        for (int y = ph - 3; y < ph; y++) {
            setIfDifferent(x, y, z, below);
        }
        setIfDifferent(x, ph, z, top);
        Block above = world.getBlockAt(x, ph + 1, z);
        if (wall != null) {
            if (above.getType() != wall) {
                above.setType(wall, false);
            }
        } else if (above.getType() == layout.wallClaimed || above.getType() == layout.wallUnclaimed) {
            above.setType(Material.AIR, false);
        }
    }

    private void setIfDifferent(int x, int y, int z, Material mat) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != mat) {
            block.setType(mat, false);
        }
    }

    // ---------- Leeren und Biome ----------

    /** Rechtecke {minX, maxX, minZ, maxZ} des Grundstuecks (nur das einzelne). */
    public List<int[]> rectOf(Plot p) {
        List<int[]> rects = new ArrayList<>();
        rects.add(new int[]{layout.min(p.x), layout.max(p.x), layout.min(p.z), layout.max(p.z)});
        return rects;
    }

    /** Rechtecke aller verbundenen Grundstuecke inklusive der Strassen dazwischen. */
    public List<int[]> groupRects(Plot start) {
        List<int[]> rects = new ArrayList<>();
        for (Plot p : group(start)) {
            rects.add(new int[]{layout.min(p.x), layout.max(p.x), layout.min(p.z), layout.max(p.z)});
            if (p.mergeEast) {
                rects.add(new int[]{layout.max(p.x) + 1, layout.min(p.x + 1) - 1, layout.min(p.z), layout.max(p.z)});
            }
            if (p.mergeSouth) {
                rects.add(new int[]{layout.min(p.x), layout.max(p.x), layout.max(p.z) + 1, layout.min(p.z + 1) - 1});
            }
            Plot east = get(p.x + 1, p.z);
            Plot south = get(p.x, p.z + 1);
            if (p.mergeEast && p.mergeSouth && east != null && south != null
                    && east.mergeSouth && south.mergeEast) {
                rects.add(new int[]{layout.max(p.x) + 1, layout.min(p.x + 1) - 1,
                        layout.max(p.z) + 1, layout.min(p.z + 1) - 1});
            }
        }
        return rects;
    }

    private List<int[]> columns(List<int[]> rects) {
        List<int[]> cols = new ArrayList<>();
        for (int[] r : rects) {
            for (int x = r[0]; x <= r[1]; x++) {
                for (int z = r[2]; z <= r[3]; z++) {
                    cols.add(new int[]{x, z});
                }
            }
        }
        return cols;
    }

    /** Setzt die Flaeche in den Ausgangszustand zurueck (verteilt ueber mehrere Ticks). */
    public void clear(List<int[]> rects, Runnable done) {
        if (world == null) {
            return;
        }
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        for (int[] r : rects) {
            BoundingBox box = new BoundingBox(r[0], minY, r[2], r[1] + 1, maxY, r[3] + 1);
            for (Entity entity : world.getNearbyEntities(box)) {
                if (!(entity instanceof Player)) {
                    entity.remove();
                }
            }
        }

        List<int[]> cols = columns(rects);
        int ph = layout.height;
        new BukkitRunnable() {
            private int index = 0;

            @Override
            public void run() {
                int end = Math.min(index + COLUMNS_PER_TICK, cols.size());
                for (; index < end; index++) {
                    int[] c = cols.get(index);
                    for (int y = minY; y < maxY; y++) {
                        Material desired;
                        if (y == minY) {
                            desired = Material.BEDROCK;
                        } else if (y < ph - 3) {
                            desired = layout.stone;
                        } else if (y < ph) {
                            desired = layout.filler;
                        } else if (y == ph) {
                            desired = layout.floor;
                        } else {
                            desired = Material.AIR;
                        }
                        Block block = world.getBlockAt(c[0], y, c[1]);
                        if (block.getType() != desired) {
                            block.setType(desired, false);
                        }
                    }
                }
                if (index >= cols.size()) {
                    cancel();
                    if (done != null) {
                        done.run();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Aendert das Biom der Flaeche (verteilt ueber mehrere Ticks). */
    public void setBiome(List<int[]> rects, Biome biome, Runnable done) {
        if (world == null) {
            return;
        }
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        List<int[]> cols = columns(rects);
        new BukkitRunnable() {
            private int index = 0;

            @Override
            public void run() {
                int end = Math.min(index + COLUMNS_PER_TICK, cols.size());
                for (; index < end; index++) {
                    int[] c = cols.get(index);
                    for (int y = minY; y < maxY; y += 4) {
                        world.setBiome(c[0], y, c[1], biome);
                    }
                }
                if (index >= cols.size()) {
                    cancel();
                    if (done != null) {
                        done.run();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ---------- Speichern ----------

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> map : yml.getMapList("plots")) {
            try {
                int x = ((Number) map.get("x")).intValue();
                int z = ((Number) map.get("z")).intValue();
                UUID owner = UUID.fromString(String.valueOf(map.get("owner")));
                long created = map.get("created") instanceof Number n ? n.longValue() : 0L;
                Plot plot = new Plot(x, z, owner, created);
                if (map.get("trusted") instanceof List<?> list) {
                    for (Object o : list) {
                        plot.trusted.add(UUID.fromString(String.valueOf(o)));
                    }
                }
                if (map.get("denied") instanceof List<?> list) {
                    for (Object o : list) {
                        plot.denied.add(UUID.fromString(String.valueOf(o)));
                    }
                }
                if (map.get("flags") instanceof Map<?, ?> flagMap) {
                    for (Map.Entry<?, ?> e : flagMap.entrySet()) {
                        plot.flags.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                    }
                }
                plot.mergeEast = Boolean.TRUE.equals(map.get("merge-east"));
                plot.mergeSouth = Boolean.TRUE.equals(map.get("merge-south"));
                plots.put(key(x, z), plot);
            } catch (Exception e) {
                plugin.getLogger().warning("Ungueltiger Eintrag in plots.yml uebersprungen: " + e.getMessage());
            }
        }
    }

    public void save() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Plot plot : plots.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("x", plot.x);
            map.put("z", plot.z);
            map.put("owner", plot.owner.toString());
            map.put("created", plot.created);
            List<String> trusted = new ArrayList<>();
            for (UUID uuid : plot.trusted) {
                trusted.add(uuid.toString());
            }
            map.put("trusted", trusted);
            List<String> denied = new ArrayList<>();
            for (UUID uuid : plot.denied) {
                denied.add(uuid.toString());
            }
            map.put("denied", denied);
            map.put("flags", new LinkedHashMap<>(plot.flags));
            map.put("merge-east", plot.mergeEast);
            map.put("merge-south", plot.mergeSouth);
            out.add(map);
        }
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("plots", out);
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte plots.yml nicht speichern: " + e.getMessage());
        }
    }
}
