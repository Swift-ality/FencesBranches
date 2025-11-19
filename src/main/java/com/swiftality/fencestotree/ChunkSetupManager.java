package com.swiftality.fencestotree;

import org.bukkit.Chunk;

import java.util.HashSet;
import java.util.Set;

public class ChunkSetupManager {
    private final FencesToTreePlugin plugin;
    private final Set<String> setupChunks = new HashSet<>();

    public ChunkSetupManager(FencesToTreePlugin plugin) {
        this.plugin = plugin;
    }

    public String getChunkId(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    public String getChunkId(String world, int x, int z) {
        return world + ":" + x + ":" + z;
    }

    public boolean isSetup(Chunk chunk) {
        return setupChunks.contains(getChunkId(chunk));
    }

    public void markSetup(Chunk chunk) {
        setupChunks.add(getChunkId(chunk));
    }

    public void markNotSetup(Chunk chunk) {
        setupChunks.remove(getChunkId(chunk));
    }

    public void resetAll() {
        plugin.getLogger().info("[ChunkSetupManager] Resetting all chunks to 'not setup' state");
        setupChunks.clear();
        
        // Mark all currently loaded chunks as not setup
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                markNotSetup(chunk);
            }
        }
        
        plugin.getLogger().info("[ChunkSetupManager] All chunks reset to 'not setup' state");
    }

    public int getSetupCount() {
        return setupChunks.size();
    }

    public void markAllLoadedAsNotSetup() {
        int count = 0;
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                markNotSetup(chunk);
                count++;
            }
        }
        plugin.getLogger().info("[ChunkSetupManager] Marked " + count + " loaded chunks as 'not setup'");
    }
}
