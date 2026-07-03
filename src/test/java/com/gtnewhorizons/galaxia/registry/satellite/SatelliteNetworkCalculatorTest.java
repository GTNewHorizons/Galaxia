package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
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
            Map.of(key(CelestialObjectId.MARS), 20L, key(CelestialObjectId.OVERWORLD), 10L),
            Map.of());

        assertEquals(20L, state.capacityKbps(CelestialObjectId.MARS));
        assertEquals(10L, state.capacityKbps(CelestialObjectId.OVERWORLD));
        assertEquals(
            10L,
            state.links()
                .get(0)
                .capacityKbps());
    }

    @Test
    void widestPathCapacityUsesWorstPlanetOnBestRoute() {
        Map<CelestialObjectKey, Long> capacity = Map.of(
            key(CelestialObjectId.MARS),
            40L,
            key(CelestialObjectId.OVERWORLD),
            10L,
            key(CelestialObjectId.EGORA),
            30L,
            key(CelestialObjectId.FROZEN_BELT),
            30L);
        List<SatelliteNetworkGraph.Edge> edges = List.of(
            new SatelliteNetworkGraph.Edge(CelestialObjectId.MARS, CelestialObjectId.OVERWORLD),
            new SatelliteNetworkGraph.Edge(CelestialObjectId.OVERWORLD, CelestialObjectId.FROZEN_BELT),
            new SatelliteNetworkGraph.Edge(CelestialObjectId.MARS, CelestialObjectId.EGORA),
            new SatelliteNetworkGraph.Edge(CelestialObjectId.EGORA, CelestialObjectId.FROZEN_BELT));

        long capacityKbps = SatelliteNetworkCalculator
            .widestPathCapacity(key(CelestialObjectId.MARS), key(CelestialObjectId.FROZEN_BELT), capacity, edges);

        assertEquals(30L, capacityKbps);
    }

    @Test
    void stateBodyUsedKbpsUsesLargestIncidentLinkUsage() {
        UUID teamId = new UUID(3L, 4L);
        SatelliteNetworkGraph.Edge marsOverworld = new SatelliteNetworkGraph.Edge(
            CelestialObjectId.MARS,
            CelestialObjectId.OVERWORLD);
        SatelliteNetworkGraph.Edge overworldFrozen = new SatelliteNetworkGraph.Edge(
            CelestialObjectId.OVERWORLD,
            CelestialObjectId.FROZEN_BELT);

        SatelliteNetworkState state = SatelliteNetworkCalculator.fromGraph(
            teamId,
            9,
            List.of(
                node(CelestialObjectId.MARS, 0.0D, 0.0D),
                node(CelestialObjectId.OVERWORLD, 10.0D, 0.0D),
                node(CelestialObjectId.FROZEN_BELT, 20.0D, 0.0D)),
            List.of(marsOverworld, overworldFrozen),
            Map.of(
                key(CelestialObjectId.MARS),
                20L,
                key(CelestialObjectId.OVERWORLD),
                30L,
                key(CelestialObjectId.FROZEN_BELT),
                20L),
            Map.of(marsOverworld, 4L, overworldFrozen, 6L));

        assertEquals(4L, state.usedKbps(CelestialObjectId.MARS));
        assertEquals(6L, state.usedKbps(CelestialObjectId.OVERWORLD));
        assertEquals(6L, state.usedKbps(CelestialObjectId.FROZEN_BELT));
    }

    @Test
    void stateBodyUsedKbpsUsesPlannedBodyUsageWhenProvided() {
        UUID teamId = new UUID(4L, 5L);
        SatelliteNetworkGraph.Edge marsOverworld = new SatelliteNetworkGraph.Edge(
            CelestialObjectId.MARS,
            CelestialObjectId.OVERWORLD);
        SatelliteNetworkGraph.Edge overworldFrozen = new SatelliteNetworkGraph.Edge(
            CelestialObjectId.OVERWORLD,
            CelestialObjectId.FROZEN_BELT);

        SatelliteNetworkState state = SatelliteNetworkCalculator.fromGraph(
            teamId,
            10,
            List.of(
                node(CelestialObjectId.MARS, 0.0D, 0.0D),
                node(CelestialObjectId.OVERWORLD, 10.0D, 0.0D),
                node(CelestialObjectId.FROZEN_BELT, 20.0D, 0.0D)),
            List.of(marsOverworld, overworldFrozen),
            Map.of(
                key(CelestialObjectId.MARS),
                20L,
                key(CelestialObjectId.OVERWORLD),
                30L,
                key(CelestialObjectId.FROZEN_BELT),
                20L),
            Map.of(marsOverworld, 4L, overworldFrozen, 6L),
            Map.of(),
            Map.of(key(CelestialObjectId.OVERWORLD), 10L));

        assertEquals(10L, state.usedKbps(CelestialObjectId.OVERWORLD));
    }

    @Test
    void forTeamBuildsLinksOnlyBetweenBodiesWithCapacity() {
        UUID teamId = new UUID(5L, 6L);

        SatelliteNetworkState state = SatelliteNetworkCalculator.forTeam(
            teamId,
            11,
            List.of(
                node(CelestialObjectId.MARS, 0.0D, 0.0D),
                node(CelestialObjectId.OVERWORLD, 10.0D, 0.0D),
                node(CelestialObjectId.FROZEN_BELT, 20.0D, 0.0D)),
            Map.of(key(CelestialObjectId.MARS), 10L, key(CelestialObjectId.FROZEN_BELT), 10L),
            Map.of());

        assertEquals(11, state.revision());
        assertEquals(
            2,
            state.bodies()
                .size());
        assertEquals(
            1,
            state.links()
                .size());
        assertEquals(
            new SatelliteNetworkGraph.Edge(CelestialObjectId.MARS, CelestialObjectId.FROZEN_BELT),
            state.links()
                .get(0)
                .asEdge());
    }

    @Test
    void strongBodiesKeepBackboneCapacityWhenWeakBodyIsBetweenThem() {
        UUID teamId = new UUID(7L, 8L);

        SatelliteNetworkState state = SatelliteNetworkCalculator.forTeam(
            teamId,
            13,
            List.of(
                node(CelestialObjectId.MARS, 0.0D, 0.0D),
                node(CelestialObjectId.EGORA, 10.0D, 0.0D),
                node(CelestialObjectId.OVERWORLD, 20.0D, 0.0D)),
            Map.of(
                key(CelestialObjectId.MARS),
                100L,
                key(CelestialObjectId.EGORA),
                10L,
                key(CelestialObjectId.OVERWORLD),
                100L),
            Map.of());

        assertEquals(
            100L,
            SatelliteNetworkCalculator.widestPath(CelestialObjectId.MARS, CelestialObjectId.OVERWORLD, state)
                .capacityKbps());
    }

    private static SatelliteNetworkGraph.Node node(CelestialObjectId id, double x, double y) {
        return new SatelliteNetworkGraph.Node(id, null, id.ordinal(), x, y, 1.0D);
    }

    private static CelestialObjectKey key(CelestialObjectId id) {
        return CelestialObjectKey.registered(id);
    }
}
