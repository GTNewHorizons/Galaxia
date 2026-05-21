package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StationBehaviors {

    private static final Map<String, StationBehavior> REGISTRY = new LinkedHashMap<>();

    public static final StationBehavior ROOM = register("room", new RoomBehavior());
    public static final StationBehavior DOCK = register("dock", new DockBehavior());

    private static StationBehavior register(String name, StationBehavior behavior) {
        REGISTRY.put(name, behavior);
        return behavior;
    }

    public static StationBehavior byName(String name) {
        return REGISTRY.getOrDefault(name, ROOM);
    }

    public static List<StationBehavior> getAll() {
        return List.copyOf(REGISTRY.values());
    }

    public static Collection<String> getAllNames() {
        return REGISTRY.keySet();
    }
}
