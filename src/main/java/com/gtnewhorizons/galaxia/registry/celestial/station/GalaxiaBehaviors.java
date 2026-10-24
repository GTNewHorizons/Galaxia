package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.gtnewhorizons.galaxia.registry.interfaces.IStationBehavior;

import lombok.Getter;

public enum GalaxiaBehaviors {

    ROOM(0, new RoomBehavior()),
    DOCK(1, new DockBehavior());

    @Getter
    private final int id;
    private final IStationBehavior behavior;

    private static final GalaxiaBehaviors[] BY_ID = new GalaxiaBehaviors[values().length];
    private static final List<IStationBehavior> ALL;

    static {
        for (var b : values()) {
            BY_ID[b.id] = b;
        }
        ALL = Arrays.stream(values())
            .sorted(Comparator.comparingInt(GalaxiaBehaviors::getId))
            .map(e -> e.behavior)
            .toList();
    }

    GalaxiaBehaviors(int id, IStationBehavior behavior) {
        this.id = id;
        this.behavior = behavior;
    }

    public IStationBehavior get() {
        return behavior;
    }

    /** Behavior with the given id, a plain room for ids that no longer exist */
    public static GalaxiaBehaviors byId(int id) {
        return id >= 0 && id < BY_ID.length && BY_ID[id] != null ? BY_ID[id] : ROOM;
    }

    public static GalaxiaBehaviors of(IStationBehavior behavior) {
        for (var b : values()) {
            if (b.behavior == behavior) return b;
        }
        return ROOM;
    }

    public static List<IStationBehavior> getAll() {
        return ALL;
    }
}
