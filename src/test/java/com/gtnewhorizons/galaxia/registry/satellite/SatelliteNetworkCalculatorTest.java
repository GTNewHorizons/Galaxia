package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class SatelliteNetworkCalculatorTest {

    @Test
    void directLinkCapacityUsesWorstEndpointKbps() {
        UUID teamId = new UUID(1L, 2L);

        SatelliteNetworkState state = SatelliteNetworkCalculator.fromGraph(
            teamId,
            7,
            List.of(node(CelestialObjectId.MARS, 0.0D, 0.0D), node(CelestialObjectId.OVERWORLD, 10.0D, 0.0D)),
            List.of(new SatelliteNetworkGraph.Edge(CelestialObjectId.MARS, CelestialObjectId.OVERWORLD)),
            Map.of(CelestialObjectId.MARS, 20L, CelestialObjectId.OVERWORLD, 10L),
            Map.of());

        assertEquals(20L, state.capacityKbps(CelestialObjectId.MARS));
        assertEquals(10L, state.capacityKbps(CelestialObjectId.OVERWORLD));
        assertEquals(10L, state.links().get(0).capacityKbps());
    }

    @Test
    void widestPathCapacityUsesWorstPlanetOnBestRoute() {
        Map<CelestialObjectId, Long> capacity = Map.of(
            CelestialObjectId.MARS,
            40L,
            CelestialObjectId.OVERWORLD,
            10L,
            CelestialObjectId.EGORA,
            30L,
            CelestialObjectId.FROZEN_BELT,
            30L);
        List<SatelliteNetworkGraph.Edge> edges = List.of(
            new SatelliteNetworkGraph.Edge(CelestialObjectId.MARS, CelestialObjectId.OVERWORLD),
            new SatelliteNetworkGraph.Edge(CelestialObjectId.OVERWORLD, CelestialObjectId.FROZEN_BELT),
            new SatelliteNetworkGraph.Edge(CelestialObjectId.MARS, CelestialObjectId.EGORA),
            new SatelliteNetworkGraph.Edge(CelestialObjectId.EGORA, CelestialObjectId.FROZEN_BELT));

        long capacityKbps = SatelliteNetworkCalculator.widestPathCapacity(
            CelestialObjectId.MARS,
            CelestialObjectId.FROZEN_BELT,
            capacity,
            edges);

        assertEquals(30L, capacityKbps);
    }

    private static SatelliteNetworkGraph.Node node(CelestialObjectId id, double x, double y) {
        return new SatelliteNetworkGraph.Node(id, null, id.ordinal(), x, y, 1.0D);
    }
}
