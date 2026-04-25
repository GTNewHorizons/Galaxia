package com.gtnewhorizons.galaxia.registry.outpost.station;

import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;

public enum StationTileState {

    EMPTY,
    OCCUPIED_OPERATIONAL,
    OCCUPIED_DISABLED,
    UNDER_CONSTRUCTION,
    UNDER_DECONSTRUCTION,
    BLOCKED;

    public boolean isOccupied() {
        return this != EMPTY;
    }

    public boolean passesSignal() {
        return this == OCCUPIED_OPERATIONAL || this == OCCUPIED_DISABLED;
    }

    public boolean isConnectorActive() {
        return this == OCCUPIED_OPERATIONAL || this == OCCUPIED_DISABLED;
    }

    public static StationTileState fromModuleStatus(Buildable.Status status) {
        return switch (status) {
            case OPERATIONAL -> OCCUPIED_OPERATIONAL;
            case DISABLED -> OCCUPIED_DISABLED;
            case CONSTRUCTION_SITE, IN_CONSTRUCTION -> UNDER_CONSTRUCTION;
            case DECONSTRUCTION -> UNDER_DECONSTRUCTION;
            case DESTROYED -> EMPTY;
        };
    }
}
