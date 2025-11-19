package com.swiftality.fencestotree;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class FencesToTreePlugin extends JavaPlugin {

    private static FencesToTreePlugin instance;
    private InspectMode inspectMode;
    private TreeGroupManager treeGroupManager;
    private OakGroupManager oakGroupManager;
    private ChunkSetupManager chunkSetupManager;
    private DebugManager debugManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize managers
        inspectMode = new InspectMode(this);
        treeGroupManager = new TreeGroupManager(this);
        oakGroupManager = new OakGroupManager(this);
        chunkSetupManager = new ChunkSetupManager(this);
        debugManager = new DebugManager();
        
        // Register Brigadier command
        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            FencesToTreeCommand command = new FencesToTreeCommand(this);
            event.registrar().register(command.createCommand());
        });
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new FenceBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new InspectListener(this), this);
        getServer().getPluginManager().registerEvents(new OakBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new PDCCleanupListener(this), this);
        getServer().getPluginManager().registerEvents(new AxeBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkLoadListener(this), this);
        
        // Start decay task
        DecayManager decayManager = new DecayManager(this);
        decayManager.startDecayTask();
        
        getLogger().info("FencesToTree plugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("FencesToTree plugin disabled!");
    }

    public static FencesToTreePlugin getInstance() {
        return instance;
    }

    public InspectMode getInspectMode() {
        return inspectMode;
    }

    public TreeGroupManager getTreeGroupManager() {
        return treeGroupManager;
    }

    public OakGroupManager getOakGroupManager() {
        return oakGroupManager;
    }

    public ChunkSetupManager getChunkSetupManager() {
        return chunkSetupManager;
    }

    public DebugManager getDebugManager() {
        return debugManager;
    }
}
