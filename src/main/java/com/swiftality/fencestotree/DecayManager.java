package com.swiftality.fencestotree;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class DecayManager {

    private final FencesToTreePlugin plugin;

    public DecayManager(FencesToTreePlugin plugin) {
        this.plugin = plugin;
    }

    public void startDecayTask() {
        if (!plugin.getConfig().getBoolean("decay.enabled", true)) {
            return;
        }

        int interval = plugin.getConfig().getInt("decay.interval", 600);

        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                    for (World world : plugin.getServer().getWorlds()) {
                        for (Chunk chunk : world.getLoadedChunks()) {
                            processChunkDecay(chunk);
                        }
                    }
                });
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    private void processChunkDecay(Chunk chunk) {
        PersistentDataContainer container = chunk.getPersistentDataContainer();
        java.util.Set<String> processedGroups = new java.util.HashSet<>();

        // Iterate through all PDC keys to find marked fences
        for (NamespacedKey key : container.getKeys()) {
            if (key.getNamespace().equalsIgnoreCase(plugin.getName()) && key.getKey().startsWith("group_")) {
                // Get group ID and check if it should decay
                String groupId = container.get(key, PersistentDataType.STRING);
                if (groupId != null && !processedGroups.contains(groupId)) {
                    processedGroups.add(groupId);
                    TreeGroup group = plugin.getTreeGroupManager().getGroup(groupId);
                    if (group != null) {
                        boolean isDecayActive = group.isDecayActive();
                        boolean shouldDecay = group.shouldDecay();
                        if (shouldDecay) {
                            plugin.getLogger().info("[DECAY] Decaying group " + groupId.substring(0, 8));
                            plugin.getTreeGroupManager().decayGroup(group);
                        }
                    }
                }
            }
        }
    }

    private boolean isOakSupportPresent(Block block) {
        return findOakLogInNetwork(block);
    }

    private boolean findOakLogInNetwork(Block startBlock) {
        Queue<Block> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        queue.add(startBlock);
        visited.add(getBlockKey(startBlock));

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

            for (BlockFace face : faces) {
                Block adjacent = current.getRelative(face);
                Material m = adjacent.getType();

                // Found oak log - connection exists
                if (isOakLog(m)) {
                    return true;
                }

                // Traverse through other fences
                String key = getBlockKey(adjacent);
                if (isFence(m) && !visited.contains(key)) {
                    visited.add(key);
                    queue.add(adjacent);
                }
            }
        }

        return false;
    }

    private String getBlockKey(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private boolean isFence(Material m) {
        return m.name().endsWith("_FENCE") || m == Material.NETHER_BRICK_FENCE;
    }

    private boolean isOakLog(Material m) {
        return m == Material.OAK_LOG || m == Material.STRIPPED_OAK_LOG;
    }
}
