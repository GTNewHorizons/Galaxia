package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class SatelliteNetworkStateTest {

    @Test
    void snapshotCopiesInputsAndExposesSanitizedKbpsValues() {
        UUID teamId = UUID.randomUUID();
        Map<CelestialObjectId, SatelliteNetworkState.Body> bodies = new EnumMap<>(CelestialObjectId.class);
        bodies.put(CelestialObjectId.MARS, new SatelliteNetworkState.Body(CelestialObjectId.MARS, -10L, 7L));
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
            CelestialObjectId.MARS,
            state.links()
                .get(0)
                .from());
        assertEquals(
            CelestialObjectId.MOON,
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
}
