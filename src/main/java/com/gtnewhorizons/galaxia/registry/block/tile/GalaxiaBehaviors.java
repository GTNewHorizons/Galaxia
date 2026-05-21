package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import lombok.Getter;

public enum GalaxiaBehaviors {

    ROOM(0, new RoomBehavior()),
    DOCK(1, new DockBehavior());

    @Getter
    private final int id;
    private final StationBehavior behavior;

    private static final GalaxiaBehaviors[] BY_ID = new GalaxiaBehaviors[values().length];
    private static final List<StationBehavior> ALL;

    static {
        for (var b : values()) {
            BY_ID[b.id] = b;
        }
        ALL = Arrays.stream(values())
            .sorted(Comparator.comparingInt(GalaxiaBehaviors::getId))
            .map(e -> e.behavior)
            .toList();
    }

    GalaxiaBehaviors(int id, StationBehavior behavior) {
        this.id = id;
        this.behavior = behavior;
    }

    public StationBehavior get() {
        return behavior;
    }

    public static GalaxiaBehaviors byId(int id) {
        return BY_ID[id];
    }

    public static GalaxiaBehaviors of(StationBehavior behavior) {
        for (var b : values()) {
            if (b.behavior == behavior) return b;
        }
        return ROOM;
    }

    public static List<StationBehavior> getAll() {
        return ALL;
    }
}
