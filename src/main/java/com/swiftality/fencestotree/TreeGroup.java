package com.swiftality.fencestotree;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public class TreeGroup {
    private final FencesToTreePlugin plugin;
    private final String groupId;
    private final Set<String> fenceLocations;
    private long decayStartTime = -1;

    public TreeGroup(FencesToTreePlugin plugin, Set<String> initialFences) {
        this.plugin = plugin;
        this.groupId = UUID.randomUUID().toString();
        this.fenceLocations = new HashSet<>(initialFences);
    }

    public String getGroupId() {
        return groupId;
    }

    public Set<String> getFenceLocations() {
        return fenceLocations;
    }

    public void setDecayStartTime(long time) {
        this.decayStartTime = time;
    }

    public long getDecayStartTime() {
        return decayStartTime;
    }

    public boolean isDecayActive() {
        return decayStartTime > 0;
    }

    public boolean shouldDecay() {
        if (!isDecayActive()) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - decayStartTime;
        return elapsed >= 60000L; // 1 minute
    }

    public void markAllFencesWithGroup(org.bukkit.World world) {
        plugin.getLogger().info("[GROUP] Marking " + fenceLocations.size() + " fences with group " + groupId);
        for (String location : fenceLocations) {
            String[] parts = location.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);

            // Get the block and set group ID
            try {
                Block block = world.getBlockAt(x, y, z);
                PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
                String encoded = location.replace(",", "_").replace("-", "n");
                NamespacedKey groupKey = new NamespacedKey(plugin, "group_" + encoded);
                plugin.getLogger().info("[GROUP] Setting group key: " + groupKey.toString() + " = " + groupId);
                container.set(groupKey, PersistentDataType.STRING, groupId);
                plugin.getLogger().info("[GROUP] Group key set successfully");
            } catch (Exception e) {
                plugin.getLogger().warning("[GROUP] Error marking fence: " + e.getMessage());
            }
        }
    }

    public static Set<String> findConnectedFences(Block startBlock, FencesToTreePlugin plugin) {
        Set<String> group = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        queue.add(startBlock);
        group.add(getBlockKey(startBlock));

        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

        while (!queue.isEmpty()) {
            Block current = queue.poll();

            for (BlockFace face : faces) {
                Block adjacent = current.getRelative(face);
                String key = getBlockKey(adjacent);

                if (isFence(adjacent.getType()) && !group.contains(key)) {
                    group.add(key);
                    queue.add(adjacent);
                }
            }
        }

        return group;
    }

    private static String getBlockKey(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private static boolean isFence(org.bukkit.Material m) {
        return m.name().endsWith("_FENCE") || m == org.bukkit.Material.NETHER_BRICK_FENCE;
    }
}
