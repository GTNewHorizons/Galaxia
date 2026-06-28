package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

/**
 * Builds the satellite communication graph and derives the immutable network snapshot used by API reads and client
 * sync.
 *
 * <p>
 * Topology is deliberately separate from data flow. The graph is built from bodies that have communication bandwidth,
 * then data transfer planning is layered on top by {@link SatelliteDataTransferPlanner}. This keeps orbital movement,
 * link capacity, and current traffic visible as separate steps.
 */
public final class SatelliteNetworkCalculator {

    private SatelliteNetworkCalculator() {}

    private record BackboneCandidate(int fromIndex, int toIndex, long capacityKbps, double distance) {}

    public static SatelliteNetworkState fromGraph(UUID teamId, int revision, List<SatelliteNetworkGraph.Node> nodes,
        List<SatelliteNetworkGraph.Edge> edges, Map<CelestialObjectId, Long> capacityByBody,
        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge) {
        return fromGraph(teamId, revision, nodes, edges, capacityByBody, usedByEdge, Map.of());
    }

    public static SatelliteNetworkState fromGraph(UUID teamId, int revision, List<SatelliteNetworkGraph.Node> nodes,
        List<SatelliteNetworkGraph.Edge> edges, Map<CelestialObjectId, Long> capacityByBody,
        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge,
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedByEdge) {
        return fromGraph(teamId, revision, nodes, edges, capacityByBody, usedByEdge, directedUsedByEdge, null);
    }

    public static SatelliteNetworkState fromGraph(UUID teamId, int revision, List<SatelliteNetworkGraph.Node> nodes,
        List<SatelliteNetworkGraph.Edge> edges, Map<CelestialObjectId, Long> capacityByBody,
        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge,
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedByEdge, Map<CelestialObjectId, Long> usedByBody) {
        Map<CelestialObjectId, Long> capacities = capacityByBody == null ? Map.of() : capacityByBody;
        Map<SatelliteNetworkGraph.Edge, Long> usedLinks = usedByEdge == null ? Map.of() : usedByEdge;
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedLinks = directedUsedByEdge == null ? Map.of()
            : directedUsedByEdge;
        Map<CelestialObjectId, Long> bodyUsage = usedByBody == null ? bodyUsageFromLinks(usedLinks)
            : sanitizedBodyUsage(usedByBody);
        Map<CelestialObjectId, SatelliteNetworkState.Body> bodies = new HashMap<>();
        for (SatelliteNetworkGraph.Node node : nodes == null ? List.<SatelliteNetworkGraph.Node>of() : nodes) {
            long capacity = capacities.getOrDefault(node.bodyId(), 0L);
            long used = bodyUsage.getOrDefault(node.bodyId(), 0L);
            bodies.put(node.bodyId(), new SatelliteNetworkState.Body(node.bodyId(), capacity, used));
        }

        List<SatelliteNetworkState.Link> links = new ArrayList<>();
        for (SatelliteNetworkGraph.Edge edge : edges == null ? List.<SatelliteNetworkGraph.Edge>of() : edges) {
            long capacity = Math.min(capacities.getOrDefault(edge.from(), 0L), capacities.getOrDefault(edge.to(), 0L));
            long used = usedLinks.getOrDefault(edge, 0L);
            long forwardUsed = directedUsedLinks
                .getOrDefault(new SatelliteNetworkGraph.DirectedEdge(edge.from(), edge.to()), 0L);
            long reverseUsed = directedUsedLinks
                .getOrDefault(new SatelliteNetworkGraph.DirectedEdge(edge.to(), edge.from()), 0L);
            links.add(new SatelliteNetworkState.Link(edge.from(), edge.to(), capacity, used, forwardUsed, reverseUsed));
        }

        return new SatelliteNetworkState(teamId, revision, bodies, links);
    }

    private static Map<CelestialObjectId, Long> bodyUsageFromLinks(Map<SatelliteNetworkGraph.Edge, Long> usedLinks) {
        Map<CelestialObjectId, Long> usedByBody = new HashMap<>();
        for (Map.Entry<SatelliteNetworkGraph.Edge, Long> entry : usedLinks.entrySet()) {
            long used = Math.max(0L, entry.getValue());
            usedByBody.merge(
                entry.getKey()
                    .from(),
                used,
                Math::max);
            usedByBody.merge(
                entry.getKey()
                    .to(),
                used,
                Math::max);
        }
        return usedByBody;
    }

    private static Map<CelestialObjectId, Long> sanitizedBodyUsage(Map<CelestialObjectId, Long> usedByBody) {
        Map<CelestialObjectId, Long> sanitized = new HashMap<>();
        for (Map.Entry<CelestialObjectId, Long> entry : usedByBody.entrySet()) {
            if (entry.getKey() == null) continue;
            sanitized.put(entry.getKey(), Math.max(0L, entry.getValue()));
        }
        return sanitized;
    }

    /*
     * Build a snapshot from raw orbital nodes and per-body capacity. Bodies with no communication bandwidth are omitted
     * from topology so they cannot become accidental relays.
     */
    public static SatelliteNetworkState forTeam(UUID teamId, int revision, List<SatelliteNetworkGraph.Node> nodes,
        Map<CelestialObjectId, Long> capacityByBody, Map<SatelliteNetworkGraph.Edge, Long> usedByEdge) {
        Map<CelestialObjectId, Long> capacities = capacityByBody == null ? Map.of() : capacityByBody;
        List<SatelliteNetworkGraph.Node> activeNodes = (nodes == null ? List.<SatelliteNetworkGraph.Node>of() : nodes)
            .stream()
            .filter(node -> capacities.getOrDefault(node.bodyId(), 0L) > 0L)
            .toList();
        List<SatelliteNetworkGraph.Edge> edges = buildTopology(activeNodes, capacities);
        return fromGraph(teamId, revision, activeNodes, edges, capacities, usedByEdge);
    }

    /*
     * Topology starts with local nearest-neighbour links, then receives a capacity-aware backbone if local links leave
     * disconnected islands.
     */
    private static List<SatelliteNetworkGraph.Edge> buildTopology(List<SatelliteNetworkGraph.Node> activeNodes,
        Map<CelestialObjectId, Long> capacities) {
        List<SatelliteNetworkGraph.Edge> localLinks = SatelliteNetworkGraph.build(activeNodes, 3);
        return withCapacityBackbone(activeNodes, capacities, localLinks);
    }

    /*
     * Public helper for callers that only need the bottleneck number and not the actual route.
     */
    public static long widestPathCapacity(CelestialObjectId from, CelestialObjectId to,
        Map<CelestialObjectId, Long> capacityByBody, List<SatelliteNetworkGraph.Edge> edges) {
        return widestPath(from, to, links(capacityByBody, edges), capacity(capacityByBody, from)).capacityKbps();
    }

    public static WidestPath widestPath(CelestialObjectId from, CelestialObjectId to, SatelliteNetworkState state) {
        if (state == null) return WidestPath.empty();
        return widestPath(from, to, state.links(), state.capacityKbps(from));
    }

    public record WidestPath(List<SatelliteNetworkGraph.Edge> edges, long capacityKbps) {

        public WidestPath {
            edges = List.copyOf(edges == null ? List.of() : edges);
            capacityKbps = Math.max(0L, capacityKbps);
        }

        public static WidestPath empty() {
            return new WidestPath(List.of(), 0L);
        }
    }

    private static WidestPath widestPath(CelestialObjectId from, CelestialObjectId to,
        List<SatelliteNetworkState.Link> links, long startCapacityKbps) {
        if (from == null || to == null) return WidestPath.empty();
        long startCapacity = Math.max(0L, startCapacityKbps);
        if (from == to) return new WidestPath(List.of(), startCapacity);

        /*
         * This is a maximin path search: each candidate route is scored by its weakest body/link capacity, then the
         * route with the largest bottleneck wins. Shortest path would make a one-satellite asteroid accidentally
         * throttle important routes just because it happens to sit between two planets.
         */
        Map<CelestialObjectId, Long> best = new HashMap<>();
        Map<CelestialObjectId, SatelliteNetworkGraph.Edge> previous = new HashMap<>();
        Set<CelestialObjectId> visited = new HashSet<>();
        best.put(from, startCapacity);

        while (true) {
            CelestialObjectId current = null;
            long currentBest = -1L;
            for (Map.Entry<CelestialObjectId, Long> entry : best.entrySet()) {
                if (!visited.contains(entry.getKey()) && entry.getValue() > currentBest) {
                    current = entry.getKey();
                    currentBest = entry.getValue();
                }
            }
            if (current == null || currentBest <= 0L) return WidestPath.empty();
            if (current == to) return buildPath(previous, from, to, currentBest);
            visited.add(current);

            for (SatelliteNetworkState.Link link : links == null ? List.<SatelliteNetworkState.Link>of() : links) {
                SatelliteNetworkGraph.Edge edge = link.asEdge();
                CelestialObjectId next = edge.from() == current ? edge.to() : edge.to() == current ? edge.from() : null;
                if (next == null || visited.contains(next)) continue;
                long candidate = Math.min(currentBest, link.capacityKbps());
                if (candidate > best.getOrDefault(next, 0L)) {
                    best.put(next, candidate);
                    previous.put(next, edge);
                }
            }
        }
    }

    private static WidestPath buildPath(Map<CelestialObjectId, SatelliteNetworkGraph.Edge> previous,
        CelestialObjectId from, CelestialObjectId to, long capacityKbps) {
        List<SatelliteNetworkGraph.Edge> edges = new ArrayList<>();
        CelestialObjectId current = to;
        while (current != from) {
            SatelliteNetworkGraph.Edge edge = previous.get(current);
            if (edge == null) return WidestPath.empty();
            edges.add(0, edge);
            current = edge.from() == current ? edge.to() : edge.from();
        }
        return new WidestPath(edges, capacityKbps);
    }

    private static List<SatelliteNetworkState.Link> links(Map<CelestialObjectId, Long> capacityByBody,
        List<SatelliteNetworkGraph.Edge> edges) {
        Map<CelestialObjectId, Long> capacities = capacityByBody == null ? Map.of() : capacityByBody;
        return (edges == null ? List.<SatelliteNetworkGraph.Edge>of() : edges).stream()
            .map(
                edge -> new SatelliteNetworkState.Link(
                    edge.from(),
                    edge.to(),
                    Math.min(capacity(capacities, edge.from()), capacity(capacities, edge.to())),
                    0L,
                    0L,
                    0L))
            .toList();
    }

    private static long capacity(Map<CelestialObjectId, Long> capacityByBody, CelestialObjectId bodyId) {
        return Math.max(
            0L,
            (capacityByBody == null ? Map.<CelestialObjectId, Long>of() : capacityByBody).getOrDefault(bodyId, 0L));
    }

    private static List<SatelliteNetworkGraph.Edge> withCapacityBackbone(List<SatelliteNetworkGraph.Node> nodes,
        Map<CelestialObjectId, Long> capacities, List<SatelliteNetworkGraph.Edge> baseEdges) {
        if (nodes == null || nodes.size() < 2) return baseEdges == null ? List.of() : List.copyOf(baseEdges);
        List<SatelliteNetworkGraph.Edge> edges = new ArrayList<>(baseEdges == null ? List.of() : baseEdges);
        int[] components = new int[nodes.size()];
        for (int i = 0; i < components.length; i++) components[i] = i;

        /*
         * The visual graph is mostly nearest-neighbour links. That can leave disconnected islands when orbital
         * positions
         * are unlucky, so add a minimal spanning backbone afterwards. Backbone candidates prefer stronger endpoints
         * first
         * and shorter distances second, which keeps the whole system connected without turning it into all-to-all
         * links.
         */
        for (BackboneCandidate candidate : backboneCandidates(nodes, capacities)) {
            if (find(components, candidate.fromIndex()) == find(components, candidate.toIndex())) continue;
            SatelliteNetworkGraph.Edge edge = new SatelliteNetworkGraph.Edge(
                nodes.get(candidate.fromIndex())
                    .bodyId(),
                nodes.get(candidate.toIndex())
                    .bodyId());
            if (!edges.contains(edge)) edges.add(edge);
            union(components, candidate.fromIndex(), candidate.toIndex());
        }
        return edges;
    }

    private static List<BackboneCandidate> backboneCandidates(List<SatelliteNetworkGraph.Node> nodes,
        Map<CelestialObjectId, Long> capacities) {
        List<BackboneCandidate> candidates = new ArrayList<>();
        for (int from = 0; from < nodes.size(); from++) {
            for (int to = from + 1; to < nodes.size(); to++) {
                candidates.add(
                    new BackboneCandidate(
                        from,
                        to,
                        Math.min(
                            capacity(
                                capacities,
                                nodes.get(from)
                                    .bodyId()),
                            capacity(
                                capacities,
                                nodes.get(to)
                                    .bodyId())),
                        distance(nodes.get(from), nodes.get(to))));
            }
        }
        candidates.sort(
            Comparator.comparingLong(BackboneCandidate::capacityKbps)
                .reversed()
                .thenComparingDouble(BackboneCandidate::distance)
                .thenComparingInt(BackboneCandidate::fromIndex)
                .thenComparingInt(BackboneCandidate::toIndex));
        return candidates;
    }

    private static double distance(SatelliteNetworkGraph.Node from, SatelliteNetworkGraph.Node to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        return Math.max(0.0D, Math.sqrt(dx * dx + dy * dy) - from.radius() - to.radius());
    }

    private static int find(int[] components, int index) {
        int parent = components[index];
        if (parent != index) components[index] = find(components, parent);
        return components[index];
    }

    private static void union(int[] components, int a, int b) {
        int rootA = find(components, a);
        int rootB = find(components, b);
        if (rootA != rootB) components[rootB] = rootA;
    }
}
