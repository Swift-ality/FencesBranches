package com.swiftality.fencestotree;

import org.bukkit.block.BlockFace;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;

public class FenceBreakListener implements Listener {

    private final FencesToTreePlugin plugin;
    private final NamespacedKey treeKey;
    private final Random random;

    public FenceBreakListener(FencesToTreePlugin plugin) {
        this.plugin = plugin;
        this.treeKey = new NamespacedKey(plugin, "is_tree");
        this.random = new Random();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // Skip if player is in inspect or decay-test mode (no drops, no processing)
        if (event.getPlayer() != null) {
            if (plugin.getInspectMode().isInspectMode(event.getPlayer()) || plugin.getInspectMode().isDecayTestMode(event.getPlayer())) {
                return;
            }
        }
        
        Block block = event.getBlock();
        String fenceType = block.getType().name();
        
        org.bukkit.Chunk chunk = block.getChunk();
        PersistentDataContainer container = chunk.getPersistentDataContainer();
        
        String key = block.getX() + "," + block.getY() + "," + block.getZ();
        NamespacedKey locationKey = new NamespacedKey(plugin, "tree_" + key.replace(",", "_").replace("-", "n"));

        // Check if block has the tree marker
            if (container.has(locationKey, PersistentDataType.BYTE)) {
                // Check if axe break should handle this
                if (plugin.getConfig().getBoolean("axe-break.enabled", true) && event.getPlayer() != null) {
                    ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
                    if (item.getType().name().endsWith("_AXE")) {
                        // Let AxeBreakListener handle this
                        return;
                    }
                }
                plugin.getLogger().info("[FencesToTree] Breaking marked fence: " + fenceType);
                // Cancel default drops
                event.setDropItems(false);
                event.setExpToDrop(0);
            
            // Update connected blocks after break to prevent cascading
            BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
            for (BlockFace face : faces) {
                Block adjacent = block.getRelative(face);
                if (adjacent.getType().name().contains("FENCE")) {
                    // Schedule block update to prevent cascade breaking
                    plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                        adjacent.setBlockData(adjacent.getBlockData());
                    }, 1L);
                }
            }

            // Run drop logic asynchronously
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                // Get drops for this fence type from config
                ConfigurationSection fenceSection = plugin.getConfig().getConfigurationSection("fence-drops." + fenceType);
                plugin.getLogger().info("[FencesToTree] Config section for " + fenceType + ": " + (fenceSection != null ? "FOUND" : "NOT FOUND"));
                
                if (fenceSection != null) {
                    List<String> dropsList = fenceSection.getStringList("drops");
                    plugin.getLogger().info("[FencesToTree] StringList drops: " + dropsList.size());
                    if (!dropsList.isEmpty()) {
                        for (String dropStr : dropsList) {
                            // Handle if drops is stored as a list of maps
                            String[] parts = dropStr.split(",");
                            // Alternative: iterate through drops section
                        }
                    } else {
                        // Try to get drops as list of maps
                        List<?> dropsMapList = fenceSection.getList("drops");
                        plugin.getLogger().info("[FencesToTree] Drops list size: " + (dropsMapList != null ? dropsMapList.size() : "null"));
                        if (dropsMapList != null && !dropsMapList.isEmpty()) {
                            for (Object dropObj : dropsMapList) {
                                if (dropObj instanceof java.util.Map) {
                                    java.util.Map<?, ?> dropMap = (java.util.Map<?, ?>) dropObj;
                                    String itemName = (String) dropMap.get("item");
                                    int amount = dropMap.get("amount") instanceof Number ? ((Number) dropMap.get("amount")).intValue() : 1;
                                    int chance = dropMap.get("chance") instanceof Number ? ((Number) dropMap.get("chance")).intValue() : 100;
                                    
                                    if (itemName != null) {
                                        int roll = random.nextInt(100);
                                        plugin.getLogger().info("[DROP] " + itemName + " chance=" + chance + " roll=" + roll + " passes=" + (roll < chance));
                                        if (roll < chance) {
                                            try {
                                                Material dropMaterial = Material.valueOf(itemName);
                                                ItemStack drop = new ItemStack(dropMaterial, amount);
                                                // Schedule drop on main thread
                                                plugin.getServer().getScheduler().runTask(plugin, () -> 
                                                    block.getWorld().dropItemNaturally(block.getLocation(), drop)
                                                );
                                                plugin.getLogger().info("[FencesToTree] Dropped: " + itemName + " x" + amount);
                                            } catch (IllegalArgumentException e) {
                                                plugin.getLogger().warning("Invalid drop material in config: " + itemName);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // If fence type not configured, drop the fence as is
                    ItemStack fenceDrop = new ItemStack(block.getType(), 1);
                    plugin.getServer().getScheduler().runTask(plugin, () -> 
                        block.getWorld().dropItemNaturally(block.getLocation(), fenceDrop)
                    );
                }
                
                // Remove the marker after breaking (async)
                plugin.getServer().getAsyncScheduler().runNow(plugin, pdcTask -> 
                    container.remove(locationKey)
                );
            });
        }
    }
}
