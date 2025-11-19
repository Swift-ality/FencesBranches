package com.swiftality.fencestotree;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InspectMode {
    private final FencesToTreePlugin plugin;
    private final Map<UUID, Boolean> inspectPlayers;
    private final Map<UUID, Boolean> decayTestPlayers;

    public InspectMode(FencesToTreePlugin plugin) {
        this.plugin = plugin;
        this.inspectPlayers = new HashMap<>();
        this.decayTestPlayers = new HashMap<>();
    }

    public void toggleInspectMode(Player player) {
        UUID playerId = player.getUniqueId();
        boolean isNowInspecting = !inspectPlayers.getOrDefault(playerId, false);
        inspectPlayers.put(playerId, isNowInspecting);

        if (isNowInspecting) {
            player.sendMessage("§a[FencesToTree] Inspect mode §aON§r - Break blocks to inspect PDC data");
        } else {
            player.sendMessage("§c[FencesToTree] Inspect mode §cOFF");
        }
    }

    public boolean isInspectMode(Player player) {
        return inspectPlayers.getOrDefault(player.getUniqueId(), false);
    }

    public void toggleDecayTestMode(Player player) {
        UUID playerId = player.getUniqueId();
        boolean isNowTesting = !decayTestPlayers.getOrDefault(playerId, false);
        decayTestPlayers.put(playerId, isNowTesting);

        if (isNowTesting) {
            player.sendMessage("§a[FencesToTree] Decay test mode §aON§r - Break fences to set their decay to 1 second");
        } else {
            player.sendMessage("§c[FencesToTree] Decay test mode §cOFF");
        }
    }

    public boolean isDecayTestMode(Player player) {
        return decayTestPlayers.getOrDefault(player.getUniqueId(), false);
    }

    public void inspectBlock(Player player, org.bukkit.block.Block block) {
        // Check if this is an oak block
        if (isOakMaterial(block.getType())) {
            inspectOakBlock(player, block);
            return;
        }
        
        // Otherwise inspect as fence
        inspectFenceBlock(player, block);
    }
    
    private void inspectOakBlock(Player player, org.bukkit.block.Block block) {
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        
        String blockCoords = block.getX() + "," + block.getY() + "," + block.getZ();
        String encoded = blockCoords.replace(",", "_").replace("-", "n");
        
        player.sendMessage("§7═══════ OAK BLOCK ═══════");
        player.sendMessage("§7Block Type: §f" + block.getType());
        player.sendMessage("§7Location: §f" + block.getX() + ", " + block.getY() + ", " + block.getZ());
        player.sendMessage(" ");
        
        // Check for oak_groups mapping
        org.bukkit.NamespacedKey mapKey = new org.bukkit.NamespacedKey(plugin, "oak_groups_" + encoded);
        String csv = container.get(mapKey, org.bukkit.persistence.PersistentDataType.STRING);
        
        if (csv == null || csv.isEmpty()) {
            // Don't show anything if no groups connected
        } else {
            player.sendMessage("§aConnected Fence Groups:");
            int count = 0;
            for (String groupId : csv.split(",")) {
                TreeGroup group = plugin.getTreeGroupManager().getGroup(groupId);
                if (group != null) {
                    String decay;
                    if (group.isDecayActive()) {
                        long elapsed = System.currentTimeMillis() - group.getDecayStartTime();
                        long remaining = Math.max(0, 60000 - elapsed);
                        long seconds = remaining / 1000;
                        decay = "§a(Decaying in " + seconds + "s)";
                    } else {
                        decay = "§c(waiting)";
                    }
                    player.sendMessage("§f  " + (++count) + ". §7Group: " + groupId.substring(0, 8) + " | §7Fences: §f" + group.getFenceLocations().size() + " " + decay);
                }
            }
        }
        
        player.sendMessage("§7═════════════════════════");
    }
    
    private void inspectFenceBlock(Player player, org.bukkit.block.Block block) {
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        
        String blockCoords = block.getX() + "," + block.getY() + "," + block.getZ();
        String encoded = blockCoords.replace(",", "_").replace("-", "n");
        
        plugin.getLogger().info("[INSPECT] Block coords: " + blockCoords);
        plugin.getLogger().info("[INSPECT] Encoded: " + encoded);
        plugin.getLogger().info("[INSPECT] Looking for: tree_" + encoded + " and group_" + encoded);
        
        player.sendMessage("§7════════════════════════════");
        player.sendMessage("§7Block Type: §f" + block.getType());
        player.sendMessage("§7Location: §f" + block.getX() + ", " + block.getY() + ", " + block.getZ());
        player.sendMessage(" ");

        boolean hasMarker = false;
        boolean hasGroup = false;
        String groupId = null;

        for (NamespacedKey key : container.getKeys()) {
            String keyStr = key.getKey();
            if (keyStr.equals("tree_" + encoded)) {
                hasMarker = true;
            } else if (keyStr.equals("group_" + encoded)) {
                hasGroup = true;
                groupId = container.get(key, org.bukkit.persistence.PersistentDataType.STRING);
            }
        }

        if (hasMarker) {
            player.sendMessage("§a✓ HAS TREE MARKER");
        } else {
            player.sendMessage("§c✗ NO TREE MARKER");
        }
        
        if (hasGroup) {
            player.sendMessage("§a✓ IN GROUP: " + (groupId != null ? groupId.substring(0, Math.min(8, groupId.length())) : "unknown"));
            TreeGroup group = plugin.getTreeGroupManager().getGroup(groupId);
            if (group != null && group.isDecayActive()) {
                long elapsed = System.currentTimeMillis() - group.getDecayStartTime();
                long remaining = Math.max(0, 60000 - elapsed);
                long seconds = remaining / 1000;
                player.sendMessage("§a✓ DECAY TIMER ACTIVE - Decays in " + seconds + "s");
            } else {
                player.sendMessage("§c✗ NO DECAY TIMER (waiting for oak break)");
            }
        } else {
            player.sendMessage("§c✗ NOT IN GROUP");
        }
        
        player.sendMessage("§7════════════════════════════");
    }
    
    private boolean isOakMaterial(org.bukkit.Material m) {
        String name = m.name();
        return (name.endsWith("_LOG") || name.endsWith("_WOOD")) &&
               (name.startsWith("OAK_") || name.startsWith("SPRUCE_") || 
                name.startsWith("BIRCH_") || name.startsWith("JUNGLE_") || 
                name.startsWith("ACACIA_") || name.startsWith("DARK_OAK_"));
    }
    public void inspectChunk(Player player) {
        org.bukkit.block.Block block = player.getLocation().getBlock();
        org.bukkit.Chunk chunk = block.getChunk();
        PersistentDataContainer container = chunk.getPersistentDataContainer();

        java.util.List<String> treeKeys = new java.util.ArrayList<>();
        java.util.List<String> groupKeys = new java.util.ArrayList<>();
        java.util.List<String> timerKeys = new java.util.ArrayList<>();
        java.util.List<String> otherKeys = new java.util.ArrayList<>();

        for (NamespacedKey key : container.getKeys()) {
            String k = key.getKey();
            if (k.startsWith("tree_")) treeKeys.add(k);
            else if (k.startsWith("group_")) groupKeys.add(k);
            else if (k.startsWith("decay_timer_")) timerKeys.add(k);
            else otherKeys.add(key.toString());
        }

        player.sendMessage("§7════ Chunk PDC Keys ════");
        sendCategory(player, "Tree markers", treeKeys);
        sendCategory(player, "Groups", groupKeys);
        sendCategory(player, "Decay timers", timerKeys);
        sendCategory(player, "Other", otherKeys);
        player.sendMessage("§7══════════════════════");
    }

    private void sendCategory(Player player, String title, java.util.List<String> keys) {
        player.sendMessage("§e" + title + ": §f" + keys.size());
        if (keys.isEmpty()) {
            player.sendMessage("  §7(none)");
            return;
        }
        int shown = 0;
        for (String k : keys) {
            if (shown++ >= 32) { // avoid spam
                player.sendMessage("  §7… and " + (keys.size() - shown + 1) + " more");
                break;
            }
            player.sendMessage("  §f- " + k);
        }
    }
}
