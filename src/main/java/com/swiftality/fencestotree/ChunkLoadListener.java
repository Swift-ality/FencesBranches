package com.swiftality.fencestotree;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.HashSet;
import java.util.Set;

public class ChunkLoadListener implements Listener {
    private final FencesToTreePlugin plugin;

    public ChunkLoadListener(FencesToTreePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Check if chunk load system is enabled
        if (!plugin.getDebugManager().isChunkLoadSystemEnabled()) {
            return;
        }
        
        Chunk chunk = event.getChunk();

        // Check if this chunk needs setup
        if (!plugin.getChunkSetupManager().isSetup(chunk)) {
            if (plugin.getDebugManager().isDebugEnabled()) {
                plugin.getLogger().info("[ChunkLoad] Processing chunk at X:" + chunk.getX() + " Z:" + chunk.getZ());
            }
            
            // Process fences in this chunk asynchronously
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                processChunkFences(chunk);
                
                // Mark chunk as setup on main thread
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getChunkSetupManager().markSetup(chunk)
                );
            });
        }
    }

    private void processChunkFences(Chunk chunk) {
        Set<Block> fencesToMark = new HashSet<>();
        int minY = 55;

        // Find all fences in the chunk at y >= 55
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < chunk.getWorld().getMaxHeight(); y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (isFence(block.getType())) {
                        fencesToMark.add(block);
                    }
                }
            }
        }

        if (!fencesToMark.isEmpty()) {
            if (plugin.getDebugManager().isDebugEnabled()) {
                plugin.getLogger().info("[ChunkLoad] Found " + fencesToMark.size() + " fences in chunk X:" + chunk.getX() + " Z:" + chunk.getZ());
            }
            // Use TreeGroupManager to process
            processChunkAsync(fencesToMark, chunk.getWorld());
        }
    }
    
    private void processChunkAsync(Set<Block> fences, org.bukkit.World world) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            Set<Block> processed = new HashSet<>();
            
            for (Block fence : fences) {
                if (!processed.contains(fence)) {
                    Set<Block> network = findFenceNetwork(fence);
                    
                    // Check if any fence in this network is connected to a log
                    if (!isNetworkConnectedToLog(network)) {
                        processed.addAll(network);
                        continue; // Skip this network, it's not connected to any log
                    }
                    
                    // Convert blocks to string locations
                    Set<String> locations = new HashSet<>();
                    for (Block b : network) {
                        locations.add(b.getX() + "," + b.getY() + "," + b.getZ());
                    }
                    
                    TreeGroup group = new TreeGroup(plugin, locations);
                    plugin.getLogger().info("[ChunkLoad] Created group " + group.getGroupId() + " with " + network.size() + " fences");
                    
                    // Mark all fences in network with tree marker
                    for (Block block : network) {
                        org.bukkit.Chunk blockChunk = block.getChunk();
                        org.bukkit.persistence.PersistentDataContainer container = blockChunk.getPersistentDataContainer();
                        String key = block.getX() + "," + block.getY() + "," + block.getZ();
                        String encoded = key.replace(",", "_").replace("-", "n");
                        org.bukkit.NamespacedKey locationKey = new org.bukkit.NamespacedKey(plugin, "tree_" + encoded);
                        container.set(locationKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                    }
                    
                    // Set group PDC
                    String groupId = group.getGroupId();
                    for (String location : locations) {
                        String[] parts = location.split(",");
                        int x = Integer.parseInt(parts[0]);
                        int y = Integer.parseInt(parts[1]);
                        int z = Integer.parseInt(parts[2]);
                        
                        Block block = world.getBlockAt(x, y, z);
                        org.bukkit.Chunk chunk = block.getChunk();
                        org.bukkit.persistence.PersistentDataContainer container = chunk.getPersistentDataContainer();
                        String encoded = location.replace(",", "_").replace("-", "n");
                        org.bukkit.NamespacedKey groupKey = new org.bukkit.NamespacedKey(plugin, "group_" + encoded);
                        container.set(groupKey, org.bukkit.persistence.PersistentDataType.STRING, groupId);
                    }
                    
                    // Attach to oaks and add group
                    plugin.getTreeGroupManager().attachGroupToOaks(world, group);
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                        plugin.getTreeGroupManager().addGroup(group)
                    );
                    
                    processed.addAll(network);
                }
            }
        });
    }
    
    private boolean isNetworkConnectedToLog(Set<Block> network) {
        org.bukkit.block.BlockFace[] faces = new org.bukkit.block.BlockFace[]{
            org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST,
            org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN
        };
        
        for (Block fence : network) {
            for (org.bukkit.block.BlockFace face : faces) {
                Block adjacent = fence.getRelative(face);
                if (isLog(adjacent.getType())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean isLog(Material m) {
        String name = m.name();
        return (name.endsWith("_LOG") || name.endsWith("_WOOD")) &&
               (name.startsWith("OAK_") || name.startsWith("SPRUCE_") || 
                name.startsWith("BIRCH_") || name.startsWith("JUNGLE_") || 
                name.startsWith("ACACIA_") || name.startsWith("DARK_OAK_") ||
                name.startsWith("MANGROVE_") || name.startsWith("CHERRY_") ||
                name.startsWith("CRIMSON_") || name.startsWith("WARPED_"));
    }

    private Set<Block> findFenceNetwork(Block startFence) {
        Set<Block> network = new HashSet<>();
        java.util.Queue<Block> queue = new java.util.LinkedList<>();
        queue.add(startFence);
        network.add(startFence);

        org.bukkit.block.BlockFace[] faces = new org.bukkit.block.BlockFace[]{
            org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST,
            org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN
        };

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            for (org.bukkit.block.BlockFace face : faces) {
                Block adjacent = current.getRelative(face);
                if (!network.contains(adjacent) && isFence(adjacent.getType())) {
                    network.add(adjacent);
                    queue.add(adjacent);
                }
            }
        }

        return network;
    }

    private boolean isFence(Material m) {
        return m.name().endsWith("_FENCE") || m == Material.NETHER_BRICK_FENCE;
    }
}
