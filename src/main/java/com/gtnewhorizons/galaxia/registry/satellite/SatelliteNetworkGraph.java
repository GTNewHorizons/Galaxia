package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

/**
 * Low-level geometry helper for visible satellite links.
 *
 * <p>
 * The builder is visual-first: prefer local parent/nearby links, cap how many links one body gets, then add only the
 * required extra links to keep satellite bodies in one connected network.
 */
public final class SatelliteNetworkGraph {

    private SatelliteNetworkGraph() {}

    public record Node(CelestialObjectKey bodyKey, CelestialObjectKey parentKey, double orbitalOrder, double x,
        double y, double radius) {

        public Node {
            if (bodyKey == null) {
                throw new IllegalArgumentException("bodyKey must be a valid celestial object key");
            }
            radius = Math.max(0.0D, radius);
        }

        /** A node with no parent and no orbital ordering, for flat graphs. */
        public Node(CelestialObjectKey bodyKey, double x, double y, double radius) {
            this(bodyKey, null, 0.0D, x, y, radius);
        }
    }

    public record Edge(CelestialObjectKey from, CelestialObjectKey to) {

        public Edge {
            if (from == null || to == null) {
                throw new IllegalArgumentException("edge endpoints must be valid celestial object keys");
            }
            if (from.equals(to)) {
                throw new IllegalArgumentException("edge endpoints must be different");
            }
            if (compareKeys(from, to) > 0) {
                CelestialObjectKey swap = from;
                from = to;
                to = swap;
            }
        }

    }

    public record DirectedEdge(CelestialObjectKey from, CelestialObjectKey to) {

        public DirectedEdge {
            if (from == null || to == null) {
                throw new IllegalArgumentException("directed edge endpoints must be valid celestial object keys");
            }
            if (from.equals(to)) {
                throw new IllegalArgumentException("directed edge endpoints must be different");
            }
        }

        public Edge asEdge() {
            return new Edge(from, to);
        }
    }

    private record Candidate(int fromIndex, int toIndex, double distance) {}

    /*
     * Produces the visual graph used by the network calculator. The result is deterministic for the same node
     * positions:
     * hierarchy first, nearest neighbours second, required island bridges last.
     */
    public static List<Edge> build(List<Node> nodes, int maxEdgesPerNode) {
        if (nodes == null || nodes.size() < 2 || maxEdgesPerNode <= 0) return List.of();
        List<Node> validNodes = nodes.stream()
            .filter(node -> node != null)
            .sorted(
                Comparator.comparingDouble(Node::orbitalOrder)
                    .thenComparing(Node::bodyKey))
            .toList();
        int[] components = new int[validNodes.size()];
        for (int i = 0; i < components.length; i++) components[i] = i;
        Map<CelestialObjectKey, Integer> edgeCounts = new HashMap<>();
        List<Edge> edges = new ArrayList<>();

        // Prefer explicit orbital hierarchy first, so child bodies tend to stay attached to their parent.
        for (int i = 0; i < validNodes.size(); i++) {
            Node node = validNodes.get(i);
            int parentIndex = nodeIndex(validNodes, node.parentKey());
            if (parentIndex < 0) continue;
            if (addEdge(validNodes.get(parentIndex), node, maxEdgesPerNode, edgeCounts, edges)) {
                union(components, parentIndex, i);
            }
        }

        List<Candidate> candidates = sortedCandidates(validNodes, false);

        // Then connect nearby bodies while respecting the per-node link cap.
        for (Candidate candidate : candidates) {
            if (find(components, candidate.fromIndex()) == find(components, candidate.toIndex())) continue;
            Node from = validNodes.get(candidate.fromIndex());
            Node to = validNodes.get(candidate.toIndex());
            if (addEdge(from, to, maxEdgesPerNode, edgeCounts, edges)) {
                union(components, candidate.fromIndex(), candidate.toIndex());
            }
        }

        // If the cap left islands, add only the missing edges needed to restore one connected network.
        for (Candidate candidate : sortedCandidates(validNodes, true)) {
            if (find(components, candidate.fromIndex()) == find(components, candidate.toIndex())) continue;
            Node from = validNodes.get(candidate.fromIndex());
            Node to = validNodes.get(candidate.toIndex());
            if (addRequiredEdge(from, to, edges)) {
                union(components, candidate.fromIndex(), candidate.toIndex());
            }
        }

        return edges;
    }

    /*
     * Candidate order is the main tie-breaker for stable topology. Child peers are skipped during the capped pass so a
     * moon cluster does not consume all nearby slots before parent links have a chance to connect.
     */
    private static List<Candidate> sortedCandidates(List<Node> validNodes, boolean includeChildPeers) {
        List<Candidate> candidates = new ArrayList<>();
        for (int from = 0; from < validNodes.size(); from++) {
            for (int to = from + 1; to < validNodes.size(); to++) {
                if (!includeChildPeers && isChildPeerCandidate(validNodes, from, to)) continue;
                candidates.add(new Candidate(from, to, edgeDistance(validNodes.get(from), validNodes.get(to))));
            }
        }
        candidates.sort(
            Comparator.comparingDouble(Candidate::distance)
                .thenComparingDouble(candidate -> Math.abs(candidate.fromIndex() - candidate.toIndex()))
                .thenComparingInt(Candidate::fromIndex)
                .thenComparingInt(Candidate::toIndex));
        return candidates;
    }

    private static int nodeIndex(List<Node> nodes, CelestialObjectKey bodyKey) {
        if (bodyKey == null) return -1;
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i)
            .bodyKey()
            .equals(bodyKey)) return i;
        return -1;
    }

    private static boolean isChildPeerCandidate(List<Node> nodes, int fromIndex, int toIndex) {
        Node from = nodes.get(fromIndex);
        Node to = nodes.get(toIndex);
        if (from.parentKey() != null && from.parentKey()
            .equals(to.bodyKey())) return false;
        if (to.parentKey() != null && to.parentKey()
            .equals(from.bodyKey())) return false;
        return nodeIndex(nodes, from.parentKey()) >= 0 || nodeIndex(nodes, to.parentKey()) >= 0;
    }

    private static double edgeDistance(Node from, Node to) {
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

    private static boolean addEdge(Node from, Node to, int maxEdgesPerNode, Map<CelestialObjectKey, Integer> edgeCounts,
        List<Edge> edges) {
        if (from.bodyKey()
            .equals(to.bodyKey())) return false;
        Edge edge = new Edge(from.bodyKey(), to.bodyKey());
        if (edges.contains(edge)) return false;
        if (edgeCounts.getOrDefault(edge.from(), 0) >= maxEdgesPerNode
            || edgeCounts.getOrDefault(edge.to(), 0) >= maxEdgesPerNode) {
            return false;
        }
        edges.add(edge);
        edgeCounts.merge(edge.from(), 1, Integer::sum);
        edgeCounts.merge(edge.to(), 1, Integer::sum);
        return true;
    }

    private static boolean addRequiredEdge(Node from, Node to, List<Edge> edges) {
        if (from.bodyKey()
            .equals(to.bodyKey())) return false;
        Edge edge = new Edge(from.bodyKey(), to.bodyKey());
        if (edges.contains(edge)) return false;
        edges.add(edge);
        return true;
    }

    private static int compareKeys(CelestialObjectKey left, CelestialObjectKey right) {
        return left.compareTo(right);
    }
}
