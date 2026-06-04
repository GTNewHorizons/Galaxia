package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.gtnewhorizons.galaxia.registry.interfaces.IStationBehavior;

import lombok.Getter;

public enum GalaxiaBehaviors {

    ROOM(0, new RoomBehavior()),
    DOCK(1, new DockBehavior()),
    POWER_ROOM(2, new PowerRoomBehavior());

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

    public static GalaxiaBehaviors byId(int id) {
        return BY_ID[id];
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
