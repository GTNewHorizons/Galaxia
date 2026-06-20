package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class SatelliteNetworkServiceTest {

    private static final UUID TEAM = new UUID(11L, 12L);

    @AfterEach
    void clearState() {
        SatelliteNetworkService.clear();
    }

    @Test
    void rebuildStoresDerivedSnapshotAndKeepsRevisionWhenContentIsUnchanged() {
        SatelliteNetworkState state = SatelliteNetworkService
            .rebuild(TEAM, nodes(), Map.of(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L), Map.of());
        SatelliteNetworkState unchanged = SatelliteNetworkService
            .rebuild(TEAM, nodes(), Map.of(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L), Map.of());

        assertSame(state, SatelliteNetworkService.current(TEAM));
        assertSame(state, unchanged);
        assertEquals(1, state.revision());
        assertEquals(10L, state.capacityKbps(CelestialObjectId.MARS));
        assertEquals(0L, state.capacityKbps(CelestialObjectId.EGORA));
        assertEquals(
            2,
            state.bodies()
                .size());
        assertEquals(
            1,
            state.links()
                .size());
    }

    @Test
    void rebuildIncrementsRevisionWhenCapacityChanges() {
        SatelliteNetworkState first = SatelliteNetworkService
            .rebuild(TEAM, nodes(), Map.of(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L), Map.of());

        SatelliteNetworkState changed = SatelliteNetworkService
            .rebuild(TEAM, nodes(), Map.of(CelestialObjectId.MARS, 20L, CelestialObjectId.OVERWORLD, 10L), Map.of());

        assertEquals(first.revision() + 1, changed.revision());
        assertEquals(20L, changed.capacityKbps(CelestialObjectId.MARS));
    }

    private static List<SatelliteNetworkGraph.Node> nodes() {
        return List.of(
            node(CelestialObjectId.MARS, 0.0D),
            node(CelestialObjectId.OVERWORLD, 10.0D),
            node(CelestialObjectId.EGORA, 20.0D));
    }

    private static SatelliteNetworkGraph.Node node(CelestialObjectId id, double x) {
        return new SatelliteNetworkGraph.Node(id, null, id.ordinal(), x, 0.0D, 1.0D);
    }
}
