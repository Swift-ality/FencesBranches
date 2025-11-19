package com.swiftality.fencestotree;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class AxeBreakListener implements Listener {
    private final FencesToTreePlugin plugin;

    public AxeBreakListener(FencesToTreePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        // Check if axe break is enabled
        if (!plugin.getConfig().getBoolean("axe-break.enabled", true)) {
            return;
        }
        
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Check if block is a fence
        if (!isFence(block.getType())) {
            return;
        }

        // Check if player is using an axe
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isAxe(item.getType())) {
            return;
        }
        
        // Check if this fence is a tree-marked fence
        if (!isTreeMarked(block)) {
            return;
        }

        // Get max fences from config
        int maxFences = plugin.getConfig().getInt("axe-break.max-fences", 6);
        
        // Get the group ID for this fence
        String groupId = getGroupId(block);
        if (groupId == null) {
            return; // Not in a valid group
        }
        
        // Get the group and find fences to break
        TreeGroup group = plugin.getTreeGroupManager().getGroup(groupId);
        if (group == null) {
            return; // Group not found
        }
        
        plugin.getLogger().info("[AXE-BREAK] Triggered on fence at " + block.getX() + "," + block.getY() + "," + block.getZ() + " in group " + groupId.substring(0, 8));
        
        // Cancel default drops first
        event.setDropItems(false);
        event.setExpToDrop(0);
        
        // Get connected fences from the same group (up to max) - do this sync since we're already on main thread
        Set<Block> fencesToBreak = findConnectedFencesInGroup(block, group, block.getWorld(), maxFences);
        
        plugin.getLogger().info("[AXE-BREAK] Found " + fencesToBreak.size() + " fences to break");

        // Check if any fence is connected to a log - if so, start decay
        boolean connectedToLog = isGroupConnectedToLog(group, block.getWorld());
        
        // Break all fences with proper drops - sync on main thread
        for (Block fence : fencesToBreak) {
            dropFenceItemsSync(fence);
            fence.setType(org.bukkit.Material.AIR);
        }
        
        // Remove broken fences from group and start decay if connected to log
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            // Remove broken fence locations from the group
            java.util.Set<String> remainingFences = new java.util.HashSet<>(group.getFenceLocations());
            for (Block fence : fencesToBreak) {
                String fenceLocation = fence.getX() + "," + fence.getY() + "," + fence.getZ();
                remainingFences.remove(fenceLocation);
            }
            
            // Update group with remaining fences
            group.getFenceLocations().clear();
            group.getFenceLocations().addAll(remainingFences);
            
            // Clean up PDC data for broken fences
            for (Block fence : fencesToBreak) {
                removePDCData(fence);
            }
            
            // If connected to log, start decay timer
            if (connectedToLog && !group.isDecayActive()) {
                group.setDecayStartTime(System.currentTimeMillis());
                plugin.getLogger().info("[AXE-BREAK] Started decay for group " + groupId.substring(0, 8) + " (connected to log)");
            }
        });
    }
    
    private boolean isTreeMarked(Block block) {
        org.bukkit.persistence.PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockCoords = block.getX() + "," + block.getY() + "," + block.getZ();
        String encoded = blockCoords.replace(",", "_").replace("-", "n");
        org.bukkit.NamespacedKey treeKey = new org.bukkit.NamespacedKey(plugin, "tree_" + encoded);
        return container.has(treeKey);
    }
    
    private String getGroupId(Block block) {
        org.bukkit.persistence.PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockCoords = block.getX() + "," + block.getY() + "," + block.getZ();
        String encoded = blockCoords.replace(",", "_").replace("-", "n");
        org.bukkit.NamespacedKey groupKey = new org.bukkit.NamespacedKey(plugin, "group_" + encoded);
        return container.get(groupKey, org.bukkit.persistence.PersistentDataType.STRING);
    }
    
    private Set<Block> findConnectedFencesInGroup(Block startBlock, TreeGroup group, org.bukkit.World world, int maxFences) {
        Set<Block> fences = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        queue.add(startBlock);
        fences.add(startBlock);
        
        // Locations belonging to this group
        Set<String> groupLocations = new java.util.HashSet<>(group.getFenceLocations());
        
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
        
        while (!queue.isEmpty() && fences.size() < maxFences) {
            Block current = queue.poll();
            
            for (BlockFace face : faces) {
                if (fences.size() >= maxFences) break;
                
                Block adjacent = current.getRelative(face);
                String adjLocation = adjacent.getX() + "," + adjacent.getY() + "," + adjacent.getZ();
                
                // Only traverse fences that are in the same group
                if (!fences.contains(adjacent) && groupLocations.contains(adjLocation)) {
                    fences.add(adjacent);
                    queue.add(adjacent);
                }
            }
        }
        
        return fences;
    }
    
    private void dropFenceItemsSync(Block block) {
        // Get the fence drops from config
        String fenceName = block.getType().name();
        org.bukkit.configuration.ConfigurationSection fenceSection = plugin.getConfig().getConfigurationSection("fence-drops." + fenceName);
        java.util.Random random = new java.util.Random();
        
        if (fenceSection != null) {
            java.util.List<?> dropsList = fenceSection.getList("drops");
            if (dropsList != null && !dropsList.isEmpty()) {
                for (Object dropObj : dropsList) {
                    if (dropObj instanceof java.util.Map) {
                        java.util.Map<?, ?> dropMap = (java.util.Map<?, ?>) dropObj;
                        String itemName = (String) dropMap.get("item");
                        int amount = dropMap.get("amount") instanceof Number ? ((Number) dropMap.get("amount")).intValue() : 1;
                        int chance = dropMap.get("chance") instanceof Number ? ((Number) dropMap.get("chance")).intValue() : 100;
                        
                        if (itemName != null && random.nextInt(100) < chance) {
                            try {
                                Material dropMaterial = Material.valueOf(itemName);
                                ItemStack drop = new ItemStack(dropMaterial, amount);
                                block.getWorld().dropItemNaturally(block.getLocation(), drop);
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("Invalid drop material in config: " + itemName);
                            }
                        }
                    }
                }
            }
        } else {
            // If no config, drop the fence itself
            ItemStack fenceDrop = new ItemStack(block.getType(), 1);
            block.getWorld().dropItemNaturally(block.getLocation(), fenceDrop);
        }
    }
    
    private void removePDCData(Block block) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            org.bukkit.persistence.PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
            String blockCoords = block.getX() + "," + block.getY() + "," + block.getZ();
            String encoded = blockCoords.replace(",", "_").replace("-", "n");
            
            // Remove tree marker
            org.bukkit.NamespacedKey treeKey = new org.bukkit.NamespacedKey(plugin, "tree_" + encoded);
            container.remove(treeKey);
            
            // Remove group association
            org.bukkit.NamespacedKey groupKey = new org.bukkit.NamespacedKey(plugin, "group_" + encoded);
            container.remove(groupKey);
            
            // Remove decay timer
            org.bukkit.NamespacedKey decayKey = new org.bukkit.NamespacedKey(plugin, "decay_timer_" + encoded);
            container.remove(decayKey);
        });
    }

    private boolean isFence(Material m) {
        return m.name().endsWith("_FENCE") || m == Material.NETHER_BRICK_FENCE;
    }

    private boolean isAxe(Material m) {
        return m.name().endsWith("_AXE");
    }
    
    private boolean isGroupConnectedToLog(TreeGroup group, org.bukkit.World world) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
        
        for (String location : group.getFenceLocations()) {
            String[] parts = location.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            
            Block fenceBlock = world.getBlockAt(x, y, z);
            for (BlockFace face : faces) {
                Block adjacent = fenceBlock.getRelative(face);
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
}
