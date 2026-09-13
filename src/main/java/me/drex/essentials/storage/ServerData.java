package me.drex.essentials.storage;

import me.drex.essentials.util.teleportation.Location;
import me.drex.essentials.util.teleportation.Warp;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ServerData {

    private final Map<String, Warp> warps = new HashMap<>();

    @Nullable
    private Location spawn = null;

    protected ServerData() {
    }

    public Map<String, Warp> getWarps() {
        return this.warps;
    }

    @Nullable
    public Location getSpawn() {
        return this.spawn;
    }

    public void setSpawn(@Nullable Location spawn) {
        this.spawn = spawn;
    }

}
