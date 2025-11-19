package com.swiftality.fencestotree;

import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TreeGroupManager {
    private final FencesToTreePlugin plugin;
    private final Map<String, TreeGroup> groupsById;

    public TreeGroupManager(FencesToTreePlugin plugin) {
        this.plugin = plugin;
        this.groupsById = new HashMap<>();
    }

    public TreeGroup createGroup(Block startFence) {
        Set<String> connectedFences = TreeGroup.findConnectedFences(startFence, plugin);
        TreeGroup group = new TreeGroup(plugin, connectedFences);
        group.markAllFencesWithGroup(startFence.getWorld());
        
        // Also mark ALL fences in the network with the group PDC (for later reference)
        markAllNetworkFencesWithGroup(startFence.getWorld(), group);
        
        attachGroupToOaks(startFence.getWorld(), group);
        groupsById.put(group.getGroupId(), group);
        return group;
    }
    
    private void markAllNetworkFencesWithGroup(org.bukkit.World world, TreeGroup group) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            String groupId = group.getGroupId();
            for (String location : group.getFenceLocations()) {
                String[] parts = location.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                
                org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                org.bukkit.Chunk chunk = block.getChunk();
                org.bukkit.persistence.PersistentDataContainer container = chunk.getPersistentDataContainer();
                String encoded = location.replace(",", "_").replace("-", "n");
                org.bukkit.NamespacedKey groupKey = new org.bukkit.NamespacedKey(plugin, "group_" + encoded);
                
                container.set(groupKey, org.bukkit.persistence.PersistentDataType.STRING, groupId);
            }
        });
    }

    public void attachGroupToOaks(org.bukkit.World world, TreeGroup group) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            org.bukkit.block.BlockFace[] faces = new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST,
                org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN
            };
            java.util.Set<String> processedOaks = new java.util.HashSet<>();
            
            // For each fence in the group, find nearby oaks
            for (String loc : group.getFenceLocations()) {
                String[] parts = loc.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                org.bukkit.block.Block fenceBlock = world.getBlockAt(x, y, z);
                
                // Check all 6 sides for oaks
                for (org.bukkit.block.BlockFace face : faces) {
                    org.bukkit.block.Block adj = fenceBlock.getRelative(face);
                    if (isOak(adj.getType())) {
                        String oakKey = encode(adj.getX(), adj.getY(), adj.getZ());
                        if (!processedOaks.contains(oakKey)) {
                            processedOaks.add(oakKey);
                            attachGroupToOak(adj, group.getGroupId());
                        }
                    }
                }
            }
        });
    }
    
    private void attachGroupToOak(org.bukkit.block.Block oakBlock, String groupId) {
        org.bukkit.Chunk chunk = oakBlock.getChunk();
        org.bukkit.persistence.PersistentDataContainer container = chunk.getPersistentDataContainer();
        String oakEncoded = encode(oakBlock.getX(), oakBlock.getY(), oakBlock.getZ());
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "oak_groups_" + oakEncoded);
        
        String existing = container.get(key, org.bukkit.persistence.PersistentDataType.STRING);
        if (existing == null || existing.isEmpty()) {
            container.set(key, org.bukkit.persistence.PersistentDataType.STRING, groupId);
        } else if (!existsInList(existing, groupId)) {
            container.set(key, org.bukkit.persistence.PersistentDataType.STRING, existing + "," + groupId);
        }
    }

    private boolean existsInList(String csv, String id) {
        for (String s : csv.split(",")) { if (s.equals(id)) return true; }
        return false;
    }
    public void addGroup(TreeGroup group) { this.groupsById.put(group.getGroupId(), group); }

    private boolean isOak(org.bukkit.Material m) {
        String name = m.name();
        return (name.endsWith("_LOG") || name.endsWith("_WOOD")) &&
               (name.startsWith("OAK_") || name.startsWith("SPRUCE_") || 
                name.startsWith("BIRCH_") || name.startsWith("JUNGLE_") || 
                name.startsWith("ACACIA_") || name.startsWith("DARK_OAK_"));
    }
    private String encode(int x, int y, int z) { return (x + "," + y + "," + z).replace(",", "_").replace("-", "n"); }

    public TreeGroup getGroup(String groupId) {
        return groupsById.get(groupId);
    }

    public TreeGroup removeGroup(String groupId) {
        return groupsById.remove(groupId);
    }

    public void decayGroup(TreeGroup group) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            if (plugin.getServer().getWorlds().isEmpty()) return;
            org.bukkit.World world = plugin.getServer().getWorlds().get(0);
            java.util.Random random = new java.util.Random();
            
            for (String location : group.getFenceLocations()) {
                String[] parts = location.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);

                try {
                    Block block = world.getBlockAt(x, y, z);
                    org.bukkit.Material fenceType = block.getType();
                    
                    if (fenceType.name().endsWith("_FENCE") || fenceType.name().equals("NETHER_BRICK_FENCE")) {
                        // Drop configured items instead of breaking naturally
                        dropConfiguredItems(block, fenceType, random);
                        
                        // Schedule block breaking on main thread
                        plugin.getServer().getScheduler().runTask(plugin, () -> 
                            block.setType(org.bukkit.Material.AIR)
                        );
                        
                        // Clean up PDC data on main thread
                        plugin.getServer().getScheduler().runTask(plugin, () -> 
                            removePDCData(block, location)
                        );
                    }
                } catch (Exception ignored) {
                }
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> 
                removeGroup(group.getGroupId())
            );
        });
    }
    
    private void removePDCData(org.bukkit.block.Block block, String location) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            org.bukkit.persistence.PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
            String encoded = location.replace(",", "_").replace("-", "n");
            
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
    
    private void dropConfiguredItems(Block block, org.bukkit.Material fenceType, java.util.Random random) {
        String fenceName = fenceType.name();
        org.bukkit.configuration.ConfigurationSection fenceSection = null;
        
        // Check if separate decay drops are enabled
        boolean useSeparateDrops = plugin.getConfig().getBoolean("decay.use-separate-drops", true);
        
        if (useSeparateDrops) {
            // Try to use decay-drops config first
            fenceSection = plugin.getConfig().getConfigurationSection("decay-drops." + fenceName);
        }
        
        // Fall back to fence-drops if decay-drops not available or disabled
        if (fenceSection == null) {
            fenceSection = plugin.getConfig().getConfigurationSection("fence-drops." + fenceName);
        }
        
        if (fenceSection != null) {
            java.util.List<?> dropsList = fenceSection.getList("drops");
            if (dropsList != null && !dropsList.isEmpty()) {
                for (Object dropObj : dropsList) {
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
                                    org.bukkit.Material dropMaterial = org.bukkit.Material.valueOf(itemName);
                                    org.bukkit.inventory.ItemStack drop = new org.bukkit.inventory.ItemStack(dropMaterial, amount);
                                    // Schedule drop on main thread
                                    plugin.getServer().getScheduler().runTask(plugin, () -> 
                                        block.getWorld().dropItemNaturally(block.getLocation(), drop)
                                    );
                                } catch (IllegalArgumentException e) {
                                    plugin.getLogger().warning("Invalid drop material in config: " + itemName);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
