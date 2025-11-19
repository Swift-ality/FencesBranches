package com.swiftality.fencestotree;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class InspectListener implements Listener {

    private final FencesToTreePlugin plugin;

    public InspectListener(FencesToTreePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (plugin.getInspectMode().isDecayTestMode(player)) {
            // Decay test mode - trigger decay for the group
            event.setCancelled(true);
            triggerDecayTest(player, event.getBlock());
        } else if (plugin.getInspectMode().isInspectMode(player)) {
            // Cancel the break event first to prevent other listeners from processing
            event.setCancelled(true);
            
            // Inspect the block
            plugin.getInspectMode().inspectBlock(player, event.getBlock());
        }
    }
    
    private void triggerDecayTest(Player player, org.bukkit.block.Block block) {
        org.bukkit.Chunk chunk = block.getChunk();
        org.bukkit.persistence.PersistentDataContainer container = chunk.getPersistentDataContainer();
        
        String blockCoords = block.getX() + "," + block.getY() + "," + block.getZ();
        String encoded = blockCoords.replace(",", "_").replace("-", "n");
        org.bukkit.NamespacedKey groupKey = new org.bukkit.NamespacedKey(plugin, "group_" + encoded);
        
        String groupId = container.get(groupKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (groupId == null) {
            player.sendMessage("§cThis fence is not in a group!");
            return;
        }
        
        TreeGroup group = plugin.getTreeGroupManager().getGroup(groupId);
        if (group == null) {
            player.sendMessage("§cGroup not found in manager!");
            return;
        }
        
        // Only report, do NOT activate decay in test mode
        player.sendMessage("§a[Decay Test] Group: " + groupId.substring(0, 8) + " | fences: " + group.getFenceLocations().size() + " | decay active: " + (group.isDecayActive() ? "YES" : "NO"));
    }
}
