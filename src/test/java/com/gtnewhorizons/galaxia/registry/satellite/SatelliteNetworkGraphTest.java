package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class SatelliteNetworkGraphTest {

    @Test
    void networkKeepsEdgesThatPassThroughBodySprites() {
        List<SatelliteNetworkGraph.Node> nodes = List.of(
            node(CelestialObjectId.MARS, null, 1.0D, 0.0D, 0.0D),
            node(CelestialObjectId.MOON, CelestialObjectId.MARS, 0.2D, 100.0D, 0.0D),
            node(CelestialObjectId.OVERWORLD, null, 2.0D, 50.0D, 0.0D));

        List<SatelliteNetworkGraph.Edge> edges = SatelliteNetworkGraph.build(nodes, 2);

        assertTrue(edges.contains(new SatelliteNetworkGraph.Edge(CelestialObjectId.MARS, CelestialObjectId.MOON)));
    }

    @Test
    void networkPrefersPerBodyEdgeCapWhenConnectivityAllows() {
        List<SatelliteNetworkGraph.Node> nodes = List.of(
            new SatelliteNetworkGraph.Node(CelestialObjectId.MARS, 0.0D, 0.0D, 5.0D),
            new SatelliteNetworkGraph.Node(CelestialObjectId.MOON, 20.0D, 0.0D, 5.0D),
            new SatelliteNetworkGraph.Node(CelestialObjectId.OVERWORLD, 0.0D, 20.0D, 5.0D));

        List<SatelliteNetworkGraph.Edge> edges = SatelliteNetworkGraph.build(nodes, 2);

        assertTrue(
            edges.stream()
                .filter(edge -> edge.touches(CelestialObjectId.MARS))
                .count() <= 2);
    }

    @Test
    void networkStaysConnectedWhenParentAttachmentsExhaustEdgeCap() {
        List<SatelliteNetworkGraph.Node> nodes = List.of(
            node(CelestialObjectId.OVERWORLD, null, 1.0D, 0.0D, 0.0D),
            node(CelestialObjectId.MOON, CelestialObjectId.OVERWORLD, 0.2D, -8.0D, 0.0D),
            node(CelestialObjectId.OVERWORLD_ORBIT, CelestialObjectId.OVERWORLD, 0.3D, 0.0D, -8.0D),
            node(CelestialObjectId.AMBERGRIS_FRAGMENT, CelestialObjectId.OVERWORLD, 0.4D, 8.0D, 0.0D),
            node(CelestialObjectId.MARS, null, 2.0D, 60.0D, 0.0D),
            node(CelestialObjectId.EGORA, null, 3.0D, 80.0D, 0.0D));

        List<SatelliteNetworkGraph.Edge> edges = SatelliteNetworkGraph.build(nodes, 3);

        assertConnected(nodes, edges);
    }

    @Test
    void networkUsesLocalGeometryInsteadOfOrbitalOrderBackbone() {
        List<SatelliteNetworkGraph.Node> nodes = List.of(
            node(CelestialObjectId.MARS, null, 1.0D, 0.0D, 0.0D),
            node(CelestialObjectId.OVERWORLD, null, 2.0D, 0.0D, 50.0D),
            node(CelestialObjectId.EGORA, null, 3.0D, 100.0D, 0.0D),
            node(CelestialObjectId.FROZEN_BELT, null, 4.0D, 140.0D, 0.0D));

        List<SatelliteNetworkGraph.Edge> edges = SatelliteNetworkGraph.build(nodes, 3);

        assertTrue(edges.contains(new SatelliteNetworkGraph.Edge(CelestialObjectId.MARS, CelestialObjectId.EGORA)));
        assertTrue(
            edges.contains(new SatelliteNetworkGraph.Edge(CelestialObjectId.EGORA, CelestialObjectId.FROZEN_BELT)));
        assertFalse(
            edges.contains(new SatelliteNetworkGraph.Edge(CelestialObjectId.OVERWORLD, CelestialObjectId.EGORA)));
    }

    @Test
    void networkAttachesChildrenToParentBeforeBackbonePeers() {
        List<SatelliteNetworkGraph.Node> nodes = List.of(
            node(CelestialObjectId.OVERWORLD, null, 3.0D, 100.0D, 0.0D),
            node(CelestialObjectId.MOON, CelestialObjectId.OVERWORLD, 0.2D, 104.0D, 8.0D),
            node(CelestialObjectId.MARS, null, 1.0D, 0.0D, 0.0D),
            node(CelestialObjectId.EGORA, null, 4.0D, 150.0D, 0.0D));

        List<SatelliteNetworkGraph.Edge> edges = SatelliteNetworkGraph.build(nodes, 3);

        assertTrue(edges.contains(new SatelliteNetworkGraph.Edge(CelestialObjectId.OVERWORLD, CelestialObjectId.MOON)));
        assertFalse(edges.contains(new SatelliteNetworkGraph.Edge(CelestialObjectId.MOON, CelestialObjectId.EGORA)));
    }

    private static SatelliteNetworkGraph.Node node(CelestialObjectId bodyId, CelestialObjectId parentId,
        double orbitalOrder, double x, double y) {
        return new SatelliteNetworkGraph.Node(bodyId, parentId, orbitalOrder, x, y, 5.0D);
    }

    private static void assertConnected(List<SatelliteNetworkGraph.Node> nodes,
        List<SatelliteNetworkGraph.Edge> edges) {
        Set<CelestialObjectId> visited = new HashSet<>();
        visit(
            nodes.get(0)
                .bodyId(),
            edges,
            visited);
        assertEquals(
            nodes.stream()
                .map(SatelliteNetworkGraph.Node::bodyId)
                .collect(java.util.stream.Collectors.toSet()),
            visited);
    }

    private static void visit(CelestialObjectId bodyId, List<SatelliteNetworkGraph.Edge> edges,
        Set<CelestialObjectId> visited) {
        if (!visited.add(bodyId)) return;
        for (SatelliteNetworkGraph.Edge edge : edges) {
            if (edge.from() == bodyId) visit(edge.to(), edges, visited);
            if (edge.to() == bodyId) visit(edge.from(), edges, visited);
        }
    }
}
