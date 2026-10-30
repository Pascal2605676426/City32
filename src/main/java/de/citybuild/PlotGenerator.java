package de.citybuild;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/** Erzeugt die flache Plot-Welt mit Grundstuecken, Strassen und Rand. */
public class PlotGenerator extends ChunkGenerator {

    private final PlotLayout layout;

    public PlotGenerator(PlotLayout layout) {
        this.layout = layout;
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        int minY = data.getMinHeight();
        int ph = layout.height;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunkX * 16 + lx;
                int wz = chunkZ * 16 + lz;

                data.setBlock(lx, minY, lz, Material.BEDROCK);

                if (isPlot(wx, wz)) {
                    data.setRegion(lx, minY + 1, lz, lx + 1, ph - 3, lz + 1, layout.stone);
                    data.setRegion(lx, ph - 3, lz, lx + 1, ph, lz + 1, layout.filler);
                    data.setBlock(lx, ph, lz, layout.floor);
                } else {
                    data.setRegion(lx, minY + 1, lz, lx + 1, ph, lz + 1, layout.stone);
                    data.setBlock(lx, ph, lz, layout.roadMat);
                    if (nearPlot(wx, wz)) {
                        data.setBlock(lx, ph + 1, lz, layout.wallUnclaimed);
                    }
                }
            }
        }
    }

    private boolean isPlot(int wx, int wz) {
        return layout.isPlotCoord(wx) && layout.isPlotCoord(wz);
    }

    private boolean nearPlot(int wx, int wz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && isPlot(wx + dx, wz + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        double x = layout.min(0) + layout.size / 2.0;
        double z = layout.road / 2.0;
        return new Location(world, x, layout.height + 1, z);
    }
}
