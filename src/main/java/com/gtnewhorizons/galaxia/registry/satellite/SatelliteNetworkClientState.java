package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.UUID;

public final class SatelliteNetworkClientState {

    private static final UUID EMPTY_TEAM_ID = new UUID(0L, 0L);

    private static SatelliteNetworkState current = SatelliteNetworkState.empty(EMPTY_TEAM_ID, 0);

    private SatelliteNetworkClientState() {}

    public static SatelliteNetworkState current() {
        return current;
    }

    public static void update(SatelliteNetworkState state) {
        if (state != null && state.revision() >= current.revision()) current = state;
    }

    public static void clear() {
        current = SatelliteNetworkState.empty(EMPTY_TEAM_ID, 0);
    }
}
