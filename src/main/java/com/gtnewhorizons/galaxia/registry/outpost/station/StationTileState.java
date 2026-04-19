package com.gtnewhorizons.galaxia.registry.outpost.station;

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
}
