package org.example.pillars.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.example.pillars.entities.Arena;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SpawnManager {

    public Location getFarthestSpawn(Arena arena, Collection<UUID> activePlayers, Collection<Location> occupied) {
        List<Location> spawns = new ArrayList<>(arena.getSpawnPoints());
        if (spawns.isEmpty()) return null;

        spawns.removeIf(occupied::contains);

        if (spawns.isEmpty()) return null;

        if (activePlayers.isEmpty()) {
            return spawns.get(ThreadLocalRandom.current().nextInt(spawns.size()));
        }

        Location bestSpawn = null;
        double bestMinDistanceSq = -1;

        for (Location candidate : spawns) {
            double cx = candidate.getX() + 0.5;
            double cz = candidate.getZ() + 0.5;
            double minDistanceSq = Double.MAX_VALUE;

            for (UUID uuid : activePlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !candidate.getWorld().equals(player.getWorld())) continue;

                Location playerLoc = player.getLocation();
                double dx = cx - playerLoc.getX();
                double dz = cz - playerLoc.getZ();

                double distanceSq = dx * dx + dz * dz;
                minDistanceSq = Math.min(minDistanceSq, distanceSq);
            }

            if (minDistanceSq > bestMinDistanceSq) {
                bestMinDistanceSq = minDistanceSq;
                bestSpawn = candidate;
            }
        }

        return bestSpawn.getBlock().getLocation();
    }

    public void prepareSpawn(Location spawn) {
        if (spawn != null) {
            spawn.getBlock().setType(org.bukkit.Material.BEDROCK);
        }
    }

    public void cleanupSpawn(Location spawn) {
        if (spawn != null && spawn.getBlock().getType() == org.bukkit.Material.BEDROCK) {
            spawn.getBlock().setType(org.bukkit.Material.AIR);
        }
    }
}
