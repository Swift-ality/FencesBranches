package com.swiftality.fencestotree;

public class DebugManager {
    private boolean debugEnabled = false;
    private boolean chunkLoadSystemEnabled = true;

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    public void toggleDebug() {
        this.debugEnabled = !debugEnabled;
    }

    public boolean isChunkLoadSystemEnabled() {
        return chunkLoadSystemEnabled;
    }

    public void setChunkLoadSystemEnabled(boolean enabled) {
        this.chunkLoadSystemEnabled = enabled;
    }

    public void toggleChunkLoadSystem() {
        this.chunkLoadSystemEnabled = !chunkLoadSystemEnabled;
    }
}
