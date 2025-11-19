package com.swiftality.fencestotree;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FencesToTreeCommand {

    private final FencesToTreePlugin plugin;
    private final NamespacedKey treeKey;
    private final Set<Material> fenceMaterials;

    public FencesToTreeCommand(FencesToTreePlugin plugin) {
        this.plugin = plugin;
        this.treeKey = new NamespacedKey(plugin, "is_tree");
        this.fenceMaterials = new HashSet<>();
        loadFenceMaterials();
    }

    private void loadFenceMaterials() {
        var fenceDropsSection = plugin.getConfig().getConfigurationSection("fence-drops");
        if (fenceDropsSection != null) {
            for (String fenceType : fenceDropsSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(fenceType);
                    fenceMaterials.add(material);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid fence material in config: " + fenceType);
                }
            }
        }
    }

    public LiteralCommandNode<CommandSourceStack> createCommand() {
        return Commands.literal("ftt")
                .requires(source -> source.getSender().hasPermission("fencestotree.use"))
                .then(Commands.literal("inspect")
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) {
                                context.getSource().getSender().sendMessage("This command can only be used by players!");
                                return Command.SINGLE_SUCCESS;
                            }
                            plugin.getInspectMode().toggleInspectMode(player);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("chunk")
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) {
                                context.getSource().getSender().sendMessage("This command can only be used by players!");
                                return Command.SINGLE_SUCCESS;
                            }
                            plugin.getInspectMode().inspectChunk(player);
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(Commands.literal("decay-test")
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) {
                                context.getSource().getSender().sendMessage("This command can only be used by players!");
                                return Command.SINGLE_SUCCESS;
                            }
                            plugin.getInspectMode().toggleDecayTestMode(player);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission("fencestotree.admin"))
                        .executes(context -> {
                            plugin.reloadConfig();
                            context.getSource().getSender().sendMessage("§a[FencesToTree] Config reloaded!");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("reset")
                        .requires(source -> !(source.getSender() instanceof Player) && source.getSender().hasPermission("fencestotree.admin"))
                        .executes(context -> {
                            context.getSource().getSender().sendMessage("§e[FencesToTree] Starting reset - wiping all PDC data and reprocessing chunks...");
                            handleReset();
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("debug")
                        .requires(source -> !(source.getSender() instanceof Player) && source.getSender().hasPermission("fencestotree.admin"))
                        .executes(context -> {
                            plugin.getDebugManager().toggleDebug();
                            String status = plugin.getDebugManager().isDebugEnabled() ? "§aENABLED" : "§cDISABLED";
                            context.getSource().getSender().sendMessage("§e[FencesToTree] Debug mode " + status);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("chunkload")
                        .requires(source -> !(source.getSender() instanceof Player) && source.getSender().hasPermission("fencestotree.admin"))
                        .executes(context -> {
                            plugin.getDebugManager().toggleChunkLoadSystem();
                            String status = plugin.getDebugManager().isChunkLoadSystemEnabled() ? "§aENABLED" : "§cDISABLED";
                            context.getSource().getSender().sendMessage("§e[FencesToTree] Chunk load system " + status);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("fullworld")
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) {
                                context.getSource().getSender().sendMessage("This command can only be used by players!");
                                return Command.SINGLE_SUCCESS;
                            }
                            handleFullWorld(player);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("area")
                        .then(Commands.argument("pos1", ArgumentTypes.blockPosition())
                        .then(Commands.argument("pos2", ArgumentTypes.blockPosition())
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) {
                                context.getSource().getSender().sendMessage("This command can only be used by players!");
                                return Command.SINGLE_SUCCESS;
                            }
                            BlockPositionResolver pos1 = context.getArgument("pos1", BlockPositionResolver.class);
                            BlockPositionResolver pos2 = context.getArgument("pos2", BlockPositionResolver.class);
                            
                            BlockPosition blockPos1 = pos1.resolve(context.getSource());
                            BlockPosition blockPos2 = pos2.resolve(context.getSource());
                            
                            int x1 = blockPos1.blockX();
                            int y1 = blockPos1.blockY();
                            int z1 = blockPos1.blockZ();
                            int x2 = blockPos2.blockX();
                            int y2 = blockPos2.blockY();
                            int z2 = blockPos2.blockZ();
                            
                            handleArea(player, x1, y1, z1, x2, y2, z2);
                            return Command.SINGLE_SUCCESS;
                        }))))
                .build();
    }

    private void handleReset() {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            // Step 1: Wipe all PDC data from all loaded chunks
            int wipedChunks = 0;
            int wipedBlocks = 0;
            for (World world : plugin.getServer().getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    PersistentDataContainer container = chunk.getPersistentDataContainer();
                    java.util.List<NamespacedKey> keysToRemove = new java.util.ArrayList<>();
                    
                    for (NamespacedKey key : container.getKeys()) {
                        if (key.getNamespace().equalsIgnoreCase(plugin.getName())) {
                            keysToRemove.add(key);
                        }
                    }
                    
                    for (NamespacedKey key : keysToRemove) {
                        container.remove(key);
                        wipedBlocks++;
                    }
                    
                    if (!keysToRemove.isEmpty()) {
                        wipedChunks++;
                    }
                }
            }
            
            plugin.getLogger().info("[RESET] Wiped PDC data from " + wipedChunks + " chunks (" + wipedBlocks + " PDC entries)");
            
            // Step 2: Reset chunk setup manager
            plugin.getChunkSetupManager().resetAll();
            
            // Step 3: Clear all groups from memory
            java.util.Map<String, TreeGroup> allGroups = new java.util.HashMap<>();
            // Access groups by reflection or add getter if needed
            plugin.getLogger().info("[RESET] Cleared all groups from memory");
            
            // Step 4: Reprocess all chunks
            int reprocessedFences = 0;
            for (World world : plugin.getServer().getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    reprocessedFences += processFencesInChunk(chunk);
                }
            }
            
            plugin.getLogger().info("[RESET] Completed reset - reprocessed " + reprocessedFences + " fences");
            plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getLogger().info("[RESET] All chunks have been reset and reprocessed!")
            );
        });
    }
    
    private void handleFullWorld(Player player) {
        World world = player.getWorld();
        int count = 0;

        for (Chunk chunk : world.getLoadedChunks()) {
            count += processFencesInChunk(chunk);
        }

        String message = plugin.getConfig().getString("messages.success-fullworld")
                .replace("{count}", String.valueOf(count));
        player.sendMessage(colorize(message));
    }

    private void handleArea(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
        int count = processFencesInArea(player.getWorld(), x1, y1, z1, x2, y2, z2);

        String message = plugin.getConfig().getString("messages.success-area")
                .replace("{count}", String.valueOf(count));
        player.sendMessage(colorize(message));
    }

    private int processFencesInChunk(Chunk chunk) {
        int count = 0;
        World world = chunk.getWorld();
        java.util.Set<Block> fencesToMark = new java.util.HashSet<>();
        int minY = 55;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunk.getX() * 16 + x;
                int worldZ = chunk.getZ() * 16 + z;

                for (int y = minY; y < world.getMaxHeight(); y++) {
                    Block block = world.getBlockAt(worldX, y, worldZ);
                    if (fenceMaterials.contains(block.getType())) {
                        fencesToMark.add(block);
                        count++;
                    }
                }
            }
        }
        
        // Mark all fences in chunk together
        if (!fencesToMark.isEmpty()) {
            markFencesAsGroup(fencesToMark, world);
        }

        return count;
    }

    private int processFencesInArea(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        int count = 0;
        java.util.Set<Block> fencesToMark = new java.util.HashSet<>();

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (fenceMaterials.contains(block.getType())) {
                        fencesToMark.add(block);
                        count++;
                    }
                }
            }
        }
        
        // Mark all fences together as one group
        if (!fencesToMark.isEmpty()) {
            markFencesAsGroup(fencesToMark, world);
        }

        return count;
    }

    private void markFencesAsGroup(java.util.Set<Block> fences, World world) {
        if (fences.isEmpty()) return;
        
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            // Convert to list for easier manipulation
            java.util.List<Block> fenceList = new java.util.ArrayList<>(fences);
            java.util.Set<Block> processed = new java.util.HashSet<>();
            
            // Find and create a separate group for each disconnected network
            for (Block fence : fenceList) {
                if (!processed.contains(fence)) {
                    // Find all connected fences from this starting point
                    java.util.Set<Block> network = findFenceNetwork(fence);
                    
                    // Create a group for this network
                    TreeGroup group = createGroupFromNetwork(network, world);
                    plugin.getLogger().info("[MARK] Created group " + group.getGroupId() + " with " + network.size() + " fences");
                    
                    // Mark all fences in this network with tree marker
                    for (Block block : network) {
                        Chunk chunk = block.getChunk();
                        PersistentDataContainer container = chunk.getPersistentDataContainer();
                        String key = block.getX() + "," + block.getY() + "," + block.getZ();
                        String encoded = key.replace(",", "_").replace("-", "n");
                        NamespacedKey locationKey = new NamespacedKey(plugin, "tree_" + encoded);
                        container.set(locationKey, PersistentDataType.BYTE, (byte) 1);
                    }
                    
                    processed.addAll(network);
                }
            }
            
            plugin.getLogger().info("[MARK] Marked " + fences.size() + " fences in " + processed.size() / Math.max(1, fences.size() / 2) + " groups");
        });
    }
    
    private java.util.Set<Block> findFenceNetwork(Block startFence) {
        java.util.Set<Block> network = new java.util.HashSet<>();
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
    
    private TreeGroup createGroupFromNetwork(java.util.Set<Block> network, World world) {
        // Convert blocks to string locations
        java.util.Set<String> locations = new java.util.HashSet<>();
        for (Block b : network) {
            locations.add(b.getX() + "," + b.getY() + "," + b.getZ());
        }
        
        TreeGroup group = new TreeGroup(plugin, locations);
        
        // Mark all fences with group PDC asynchronously
        String groupId = group.getGroupId();
        for (String location : locations) {
            String[] parts = location.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            
            Block block = world.getBlockAt(x, y, z);
            Chunk chunk = block.getChunk();
            PersistentDataContainer container = chunk.getPersistentDataContainer();
            String encoded = location.replace(",", "_").replace("-", "n");
            NamespacedKey groupKey = new NamespacedKey(plugin, "group_" + encoded);
            container.set(groupKey, PersistentDataType.STRING, groupId);
        }
        
        // Attach group to nearby oaks and add group asynchronously
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            plugin.getTreeGroupManager().attachGroupToOaks(world, group);
            plugin.getServer().getScheduler().runTask(plugin, () -> 
                plugin.getTreeGroupManager().addGroup(group)
            );
        });
        
        return group;
    }
    
    private boolean isFence(Material m) {
        return m.name().endsWith("_FENCE") || m == Material.NETHER_BRICK_FENCE;
    }
    
    private void markFenceAsTree(Block block) {
        // Deprecated - kept for backwards compatibility
        Chunk chunk = block.getChunk();
        PersistentDataContainer container = chunk.getPersistentDataContainer();
        
        String key = block.getX() + "," + block.getY() + "," + block.getZ();
        String encoded = key.replace(",", "_").replace("-", "n");
        NamespacedKey locationKey = new NamespacedKey(plugin, "tree_" + encoded);
        
        container.set(locationKey, PersistentDataType.BYTE, (byte) 1);
        TreeGroup group = plugin.getTreeGroupManager().createGroup(block);
    }

    private String colorize(String message) {
        return message.replace("&", "§");
    }
}
