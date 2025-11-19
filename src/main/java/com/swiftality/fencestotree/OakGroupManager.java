package com.swiftality.fencestotree;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OakGroupManager {
    private final FencesToTreePlugin plugin;
    private final Map<String, Set<String>> oakToFenceGroups = new HashMap<>();

    public OakGroupManager(FencesToTreePlugin plugin) {
        this.plugin = plugin;
    }

    public void registerFenceGroupsForOak(Block oakBlock) {
        String oakKey = getBlockKey(oakBlock);
        Set<String> connectedGroups = new HashSet<>();

        // BFS to find all fence groups connected to this oak
        java.util.Queue<Block> queue = new java.util.LinkedList<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        queue.add(oakBlock);
        visited.add(oakKey);

        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

        while (!queue.isEmpty()) {
            Block current = queue.poll();

            for (BlockFace face : faces) {
                Block adjacent = current.getRelative(face);
                String key = getBlockKey(adjacent);

                if (!visited.contains(key)) {
                    visited.add(key);

                    // If it's a fence, get its group
                    if (isFence(adjacent.getType())) {
                        String groupId = getGroupIdForBlock(adjacent);
                        if (groupId != null) {
                            connectedGroups.add(groupId);
                        }
                        queue.add(adjacent);
                    }
                    // Continue through oak logs
                    else if (isOakMaterial(adjacent.getType())) {
                        queue.add(adjacent);
                    }
                }
            }
        }

        if (!connectedGroups.isEmpty()) {
            oakToFenceGroups.put(oakKey, connectedGroups);
            plugin.getLogger().info("[OAK-GROUP] Oak at " + oakKey + " connected to " + connectedGroups.size() + " fence groups");
        }
    }

    public Set<String> getFenceGroupsForOak(Block oakBlock) {
        return oakToFenceGroups.getOrDefault(getBlockKey(oakBlock), new HashSet<>());
    }

    private String getGroupIdForBlock(Block block) {
        org.bukkit.persistence.PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockCoords = block.getX() + "," + block.getY() + "," + block.getZ();
        String encoded = blockCoords.replace(",", "_").replace("-", "n");
        org.bukkit.NamespacedKey groupKey = new org.bukkit.NamespacedKey(plugin, "group_" + encoded);
        return container.get(groupKey, org.bukkit.persistence.PersistentDataType.STRING);
    }

    private String getBlockKey(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private boolean isFence(org.bukkit.Material m) {
        return m.name().endsWith("_FENCE") || m == org.bukkit.Material.NETHER_BRICK_FENCE;
    }

    private boolean isOakMaterial(org.bukkit.Material m) {
        String name = m.name();
        return (name.endsWith("_LOG") || name.endsWith("_WOOD")) &&
               (name.startsWith("OAK_") || name.startsWith("SPRUCE_") || 
                name.startsWith("BIRCH_") || name.startsWith("JUNGLE_") || 
                name.startsWith("ACACIA_") || name.startsWith("DARK_OAK_"));
    }
}
