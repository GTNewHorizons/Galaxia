package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class SatelliteNetworkClientStateTest {

    @AfterEach
    void clear() {
        SatelliteNetworkClientState.clear();
    }

    @Test
    void updateKeepsNewestSnapshotByRevision() {
        SatelliteNetworkState newer = state(2, 10L);
        SatelliteNetworkState older = state(1, 20L);

        SatelliteNetworkClientState.update(newer);
        SatelliteNetworkClientState.update(older);

        assertEquals(
            2,
            SatelliteNetworkClientState.current()
                .revision());
        assertEquals(
            10L,
            SatelliteNetworkClientState.current()
                .capacityKbps(CelestialObjectId.MARS));
    }

    private static SatelliteNetworkState state(int revision, long capacityKbps) {
        return new SatelliteNetworkState(
            new UUID(1L, 2L),
            revision,
            Map.of(CelestialObjectId.MARS, new SatelliteNetworkState.Body(CelestialObjectId.MARS, capacityKbps, 0L)),
            java.util.List.of());
    }
}
