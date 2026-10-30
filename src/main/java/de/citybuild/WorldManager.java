package de.citybuild;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;

import java.util.Random;
import java.util.function.Consumer;

/** Farmwelt anlegen und sichere Zufallspositionen (Farmwelt/Nether) finden. */
public class WorldManager {

    private final CityBuildPlugin plugin;
    private final Random random = new Random();

    public WorldManager(CityBuildPlugin plugin) {
        this.plugin = plugin;
        loadFarmWorld();
    }

    private String farmName() {
        return plugin.getConfig().getString("worlds.farmworld.name", "farmwelt");
    }

    private void loadFarmWorld() {
        if (!plugin.getConfig().getBoolean("worlds.farmworld.enabled", true)) {
            return;
        }
        String name = farmName();
        try {
            World world = Bukkit.getWorld(name);
            if (world == null) {
                world = WorldCreator.name(name).environment(World.Environment.NORMAL).createWorld();
            }
            if (world == null) {
                plugin.getLogger().warning("Die Farmwelt '" + name + "' konnte nicht erstellt werden.");
                return;
            }
            double border = plugin.getConfig().getDouble("worlds.farmworld.border-size", 6000);
            if (border > 0) {
                world.getWorldBorder().setCenter(0, 0);
                world.getWorldBorder().setSize(border);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Erstellen der Farmwelt: " + e.getMessage());
        }
    }

    public World farmWorld() {
        return Bukkit.getWorld(farmName());
    }

    public World findByEnvironment(World.Environment environment) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == environment) {
                return world;
            }
        }
        return null;
    }

    /** Sucht eine sichere Zufallsposition. callback bekommt null, wenn nichts gefunden wurde. */
    public void findSafe(World world, int radius, int attempts, Consumer<Location> callback) {
        int x = random.nextInt(radius * 2 + 1) - radius;
        int z = random.nextInt(radius * 2 + 1) - radius;
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            Location location = check(world, x, z);
            if (location != null) {
                callback.accept(location);
            } else if (attempts > 1) {
                findSafe(world, radius, attempts - 1, callback);
            } else {
                callback.accept(null);
            }
        });
    }

    private Location check(World world, int x, int z) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            for (int y = 120; y > 32; y--) {
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);
                Block ground = world.getBlockAt(x, y - 1, z);
                if (feet.isEmpty() && head.isEmpty() && isSafeGround(ground)) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
            return null;
        }
        int y = world.getHighestBlockYAt(x, z);
        Block ground = world.getBlockAt(x, y, z);
        if (!isSafeGround(ground)) {
            return null;
        }
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    private boolean isSafeGround(Block block) {
        Material type = block.getType();
        return type.isSolid()
                && !Tag.LEAVES.isTagged(type)
                && type != Material.MAGMA_BLOCK
                && type != Material.CACTUS
                && type != Material.CAMPFIRE
                && type != Material.SOUL_CAMPFIRE;
    }
}
