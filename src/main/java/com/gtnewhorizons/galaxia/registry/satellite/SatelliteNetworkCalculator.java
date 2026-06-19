package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public final class SatelliteNetworkCalculator {

    private SatelliteNetworkCalculator() {}

    public static SatelliteNetworkState fromGraph(UUID teamId, int revision, List<SatelliteNetworkGraph.Node> nodes,
        List<SatelliteNetworkGraph.Edge> edges, Map<CelestialObjectId, Long> capacityByBody,
        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge) {
        Map<CelestialObjectId, Long> capacities = capacityByBody == null ? Map.of() : capacityByBody;
        Map<SatelliteNetworkGraph.Edge, Long> usedLinks = usedByEdge == null ? Map.of() : usedByEdge;
        Map<CelestialObjectId, SatelliteNetworkState.Body> bodies = new HashMap<>();
        for (SatelliteNetworkGraph.Node node : nodes == null ? List.<SatelliteNetworkGraph.Node>of() : nodes) {
            long capacity = capacities.getOrDefault(node.bodyId(), 0L);
            bodies.put(node.bodyId(), new SatelliteNetworkState.Body(node.bodyId(), capacity, 0L));
        }

        List<SatelliteNetworkState.Link> links = new ArrayList<>();
        for (SatelliteNetworkGraph.Edge edge : edges == null ? List.<SatelliteNetworkGraph.Edge>of() : edges) {
            long capacity = Math.min(capacities.getOrDefault(edge.from(), 0L), capacities.getOrDefault(edge.to(), 0L));
            long used = usedLinks.getOrDefault(edge, 0L);
            links.add(new SatelliteNetworkState.Link(edge.from(), edge.to(), capacity, used));
        }

        return new SatelliteNetworkState(teamId, revision, bodies, links);
    }

    public static long widestPathCapacity(CelestialObjectId from, CelestialObjectId to,
        Map<CelestialObjectId, Long> capacityByBody, List<SatelliteNetworkGraph.Edge> edges) {
        if (from == null || to == null) return 0L;
        Map<CelestialObjectId, Long> capacities = capacityByBody == null ? Map.of() : capacityByBody;
        if (from == to) return Math.max(0L, capacities.getOrDefault(from, 0L));

        List<SatelliteNetworkGraph.Edge> links = edges == null ? List.of() : edges;
        Map<CelestialObjectId, Long> best = new HashMap<>();
        Set<CelestialObjectId> visited = new HashSet<>();
        best.put(from, Math.max(0L, capacities.getOrDefault(from, 0L)));

        while (true) {
            CelestialObjectId current = null;
            long currentBest = -1L;
            for (Map.Entry<CelestialObjectId, Long> entry : best.entrySet()) {
                if (!visited.contains(entry.getKey()) && entry.getValue() > currentBest) {
                    current = entry.getKey();
                    currentBest = entry.getValue();
                }
            }
            if (current == null || currentBest <= 0L) return 0L;
            if (current == to) return currentBest;
            visited.add(current);

            for (SatelliteNetworkGraph.Edge edge : links) {
                CelestialObjectId next = edge.from() == current ? edge.to()
                    : edge.to() == current ? edge.from()
                        : null;
                if (next == null || visited.contains(next)) continue;
                long nextCapacity = Math.max(0L, capacities.getOrDefault(next, 0L));
                long candidate = Math.min(currentBest, nextCapacity);
                if (candidate > best.getOrDefault(next, 0L)) best.put(next, candidate);
            }
        }
    }
}
