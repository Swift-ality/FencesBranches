package com.swiftality.fencestotree;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class OakBreakListener implements Listener {

    private final FencesToTreePlugin plugin;

    public OakBreakListener(FencesToTreePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onOakBreak(BlockBreakEvent event) {
        // Skip if in inspect mode
        if (event.getPlayer() != null && plugin.getInspectMode().isInspectMode(event.getPlayer())) {
            return;
        }
        
        Block block = event.getBlock();
        Material type = block.getType();

        // Check if broken block is oak
        if (isOakMaterial(type)) {
            plugin.getLogger().info("[OAK BREAK] Oak block broken at " + block.getX() + "," + block.getY() + "," + block.getZ());
            
            // Read PDC mapping from oak to groups
            org.bukkit.Chunk chunk = block.getChunk();
            org.bukkit.persistence.PersistentDataContainer container = chunk.getPersistentDataContainer();
            String oakEncoded = (block.getX() + "," + block.getY() + "," + block.getZ()).replace(",", "_").replace("-", "n");
            org.bukkit.NamespacedKey mapKey = new org.bukkit.NamespacedKey(plugin, "oak_groups_" + oakEncoded);
            String csv = container.get(mapKey, org.bukkit.persistence.PersistentDataType.STRING);
            if (csv == null || csv.isEmpty()) {
                plugin.getLogger().info("[OAK BREAK] No connected fence groups recorded for this oak");
                return;
            }
            for (String groupId : csv.split(",")) {
                TreeGroup group = plugin.getTreeGroupManager().getGroup(groupId);
                if (group != null && !group.isDecayActive()) {
                    group.setDecayStartTime(System.currentTimeMillis());
                    plugin.getLogger().info("[OAK BREAK] Started decay for group " + groupId.substring(0, 8));
                }
            }
        }
    }


    private String getBlockKey(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private boolean isFence(Material m) {
        return m.name().endsWith("_FENCE") || m == Material.NETHER_BRICK_FENCE;
    }

    private boolean isOakMaterial(Material m) {
        String name = m.name();
        return (name.endsWith("_LOG") || name.endsWith("_WOOD")) &&
               (name.startsWith("OAK_") || name.startsWith("SPRUCE_") || 
                name.startsWith("BIRCH_") || name.startsWith("JUNGLE_") || 
                name.startsWith("ACACIA_") || name.startsWith("DARK_OAK_"));
    }
}
