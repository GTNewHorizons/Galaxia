package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

final class SatelliteNetworkStateTest {

    @Test
    void snapshotCopiesInputsAndExposesSanitizedKbpsValues() {
        UUID teamId = UUID.randomUUID();
        Map<CelestialObjectKey, SatelliteNetworkState.Body> bodies = new HashMap<>();
        bodies.put(
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            new SatelliteNetworkState.Body(CelestialObjectId.MARS, -10L, 7L));
        List<SatelliteNetworkState.Link> links = new ArrayList<>();
        links.add(new SatelliteNetworkState.Link(CelestialObjectId.MOON, CelestialObjectId.MARS, 20L, -5L, 3L, 8L));

        SatelliteNetworkState state = new SatelliteNetworkState(teamId, 3, bodies, links);
        bodies.clear();
        links.clear();

        assertEquals(teamId, state.teamId());
        assertEquals(3, state.revision());
        assertEquals(0L, state.capacityKbps(CelestialObjectId.MARS));
        assertEquals(7L, state.usedKbps(CelestialObjectId.MARS));
        assertEquals(0L, state.capacityKbps(CelestialObjectId.EGORA));
        assertEquals(
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            state.links()
                .get(0)
                .from());
        assertEquals(
            CelestialObjectKey.registered(CelestialObjectId.MOON),
            state.links()
                .get(0)
                .to());
        assertEquals(
            20L,
            state.links()
                .get(0)
                .capacityKbps());
        assertEquals(
            0L,
            state.links()
                .get(0)
                .usedKbps());
        assertEquals(
            8L,
            state.links()
                .get(0)
                .forwardUsedKbps());
        assertEquals(
            3L,
            state.links()
                .get(0)
                .reverseUsedKbps());
        assertEquals(
            8L,
            state.links()
                .get(0)
                .usedKbps(CelestialObjectId.MARS, CelestialObjectId.MOON));
        assertEquals(
            3L,
            state.links()
                .get(0)
                .usedKbps(CelestialObjectId.MOON, CelestialObjectId.MARS));
        assertThrows(
            UnsupportedOperationException.class,
            () -> state.links()
                .clear());
        assertThrows(
            UnsupportedOperationException.class,
            () -> state.bodies()
                .clear());
    }

    @Test
    void snapshotCanExposeMinorBodyNetworkUsage() {
        UUID teamId = UUID.randomUUID();
        CelestialObjectKey asteroid = asteroidKey(12);
        CelestialObjectKey belt = CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT);
        SatelliteDataKey dataKey = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);

        SatelliteNetworkState state = new SatelliteNetworkState(
            teamId,
            4,
            Map.of(asteroid, new SatelliteNetworkState.Body(asteroid, 40L, 5L)),
            List.of(new SatelliteNetworkState.Link(belt, asteroid, 20L, 3L, 2L, 1L)),
            List.of(new SatelliteNetworkState.PendingData(asteroid, List.of(belt), dataKey, 30L)));

        assertEquals(40L, state.capacityKbps(asteroid));
        assertEquals(5L, state.usedKbps(asteroid));
        assertEquals(
            List.of(belt),
            state.pendingData(asteroid)
                .get(0)
                .destinationBodyKeys());
        assertEquals(
            2L,
            state.links()
                .get(0)
                .usedKbps(belt, asteroid));
    }

    private static CelestialObjectKey asteroidKey(int index) {
        return CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, index));
    }
}
