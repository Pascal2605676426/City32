package de.citybuild;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Geometrie der Plot-Welt. Die Welt besteht aus Zellen der Groesse (size + road).
 * In jeder Zelle liegt zuerst die Strasse (road Bloecke), danach das Grundstueck (size Bloecke).
 */
public final class PlotLayout {

    public final int size;
    public final int road;
    public final int cell;
    public final int height;

    public final Material stone;
    public final Material filler;
    public final Material floor;
    public final Material roadMat;
    public final Material wallUnclaimed;
    public final Material wallClaimed;

    public PlotLayout(FileConfiguration cfg) {
        this.size = clamp(cfg.getInt("plots.plot-size", 32), 8, 128);
        this.road = clamp(cfg.getInt("plots.road-width", 7), 3, 16);
        this.cell = size + road;
        this.height = clamp(cfg.getInt("plots.plot-height", 64), -50, 300);
        this.stone = mat(cfg, "plots.materials.stone", Material.STONE);
        this.filler = mat(cfg, "plots.materials.filler", Material.DIRT);
        this.floor = mat(cfg, "plots.materials.floor", Material.GRASS_BLOCK);
        this.roadMat = mat(cfg, "plots.materials.road", Material.QUARTZ_BLOCK);
        this.wallUnclaimed = mat(cfg, "plots.materials.wall-unclaimed", Material.STONE_SLAB);
        this.wallClaimed = mat(cfg, "plots.materials.wall-claimed", Material.SANDSTONE_SLAB);
    }

    private static Material mat(FileConfiguration cfg, String path, Material def) {
        Material m = Material.matchMaterial(cfg.getString(path, def.name()));
        return m == null ? def : m;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Nummer der Zelle fuer eine Weltkoordinate. */
    public int cellOf(int v) {
        return Math.floorDiv(v, cell);
    }

    /** true, wenn die Koordinate im Grundstuecksbereich (nicht auf der Strasse) liegt. */
    public boolean isPlotCoord(int v) {
        return Math.floorMod(v, cell) >= road;
    }

    /** Kleinste Koordinate des Grundstuecks mit dieser Nummer. */
    public int min(int id) {
        return id * cell + road;
    }

    /** Groesste Koordinate des Grundstuecks mit dieser Nummer. */
    public int max(int id) {
        return id * cell + cell - 1;
    }
}
