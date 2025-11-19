package com.swiftality.fencestotree;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PDCCleanupListener implements Listener {
    private final FencesToTreePlugin plugin;

    public PDCCleanupListener(FencesToTreePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Block block = event.getBlock();
        Material type = block.getType();

        // Clean up PDC data for fences and oak blocks
        if (isFence(type) || isOakMaterial(type)) {
            removePDCData(block);
        }
    }

    private void removePDCData(Block block) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            PersistentDataContainer container = block.getChunk().getPersistentDataContainer();

            String blockCoords = block.getX() + "," + block.getY() + "," + block.getZ();
            String encoded = blockCoords.replace(",", "_").replace("-", "n");

            // Remove tree marker
            NamespacedKey treeKey = new NamespacedKey(plugin, "tree_" + encoded);
            container.remove(treeKey);

            // Remove group association
            NamespacedKey groupKey = new NamespacedKey(plugin, "group_" + encoded);
            container.remove(groupKey);

            // Remove decay timer
            NamespacedKey decayKey = new NamespacedKey(plugin, "decay_timer_" + encoded);
            container.remove(decayKey);
            
            // Remove oak mapping if this was an oak block
            NamespacedKey oakMapKey = new NamespacedKey(plugin, "oak_groups_" + encoded);
            container.remove(oakMapKey);

            plugin.getLogger().info("[PDC-CLEANUP] Removed PDC data for block at " + blockCoords);
        });
    }

    private boolean isFence(Material m) {
        return m.name().endsWith("_FENCE") || m == Material.NETHER_BRICK_FENCE;
    }

    private boolean isOakMaterial(Material m) {
        return m == Material.OAK_LOG || m == Material.STRIPPED_OAK_LOG ||
               m == Material.OAK_WOOD || m == Material.STRIPPED_OAK_WOOD;
    }
}
