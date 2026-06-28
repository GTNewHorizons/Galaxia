package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

/**
 * Low-level geometry helper for visible satellite links.
 *
 * <p>
 * The builder is visual-first: prefer local parent/nearby links, cap how many links one body gets, then add only the
 * required extra links to keep satellite bodies in one connected network.
 */
public final class SatelliteNetworkGraph {

    private SatelliteNetworkGraph() {}

    public record Node(CelestialObjectId bodyId, CelestialObjectId parentId, double orbitalOrder, double x, double y,
        double radius) {

        public Node {
            if (bodyId == null || bodyId == CelestialObjectId.INVALID) {
                throw new IllegalArgumentException("bodyId must be a valid celestial object id");
            }
            if (parentId == CelestialObjectId.INVALID) parentId = null;
            radius = Math.max(0.0D, radius);
        }

        public Node(CelestialObjectId bodyId, double x, double y, double radius) {
            this(bodyId, null, x, x, y, radius);
        }
    }

    public record Edge(CelestialObjectId from, CelestialObjectId to) {

        public Edge {
            if (from == null || from == CelestialObjectId.INVALID || to == null || to == CelestialObjectId.INVALID) {
                throw new IllegalArgumentException("edge endpoints must be valid celestial object ids");
            }
            if (from == to) {
                throw new IllegalArgumentException("edge endpoints must be different");
            }
            if (from.ordinal() > to.ordinal()) {
                CelestialObjectId swap = from;
                from = to;
                to = swap;
            }
        }

        public boolean touches(CelestialObjectId bodyId) {
            return from == bodyId || to == bodyId;
        }
    }

    public record DirectedEdge(CelestialObjectId from, CelestialObjectId to) {

        public DirectedEdge {
            if (from == null || from == CelestialObjectId.INVALID || to == null || to == CelestialObjectId.INVALID) {
                throw new IllegalArgumentException("directed edge endpoints must be valid celestial object ids");
            }
            if (from == to) {
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
                    .thenComparingInt(
                        node -> node.bodyId()
                            .ordinal()))
            .toList();
        int[] components = new int[validNodes.size()];
        for (int i = 0; i < components.length; i++) components[i] = i;
        int[] edgeCounts = new int[CelestialObjectId.values().length];
        List<Edge> edges = new ArrayList<>();

        // Prefer explicit orbital hierarchy first, so child bodies tend to stay attached to their parent.
        for (int i = 0; i < validNodes.size(); i++) {
            Node node = validNodes.get(i);
            int parentIndex = nodeIndex(validNodes, node.parentId());
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

    private static int nodeIndex(List<Node> nodes, CelestialObjectId bodyId) {
        if (bodyId == null) return -1;
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i)
            .bodyId() == bodyId) return i;
        return -1;
    }

    private static boolean isChildPeerCandidate(List<Node> nodes, int fromIndex, int toIndex) {
        Node from = nodes.get(fromIndex);
        Node to = nodes.get(toIndex);
        if (from.parentId() == to.bodyId() || to.parentId() == from.bodyId()) return false;
        return nodeIndex(nodes, from.parentId()) >= 0 || nodeIndex(nodes, to.parentId()) >= 0;
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

    private static boolean addEdge(Node from, Node to, int maxEdgesPerNode, int[] edgeCounts, List<Edge> edges) {
        if (from.bodyId() == to.bodyId()) return false;
        Edge edge = new Edge(from.bodyId(), to.bodyId());
        if (edges.contains(edge)) return false;
        if (edgeCounts[edge.from()
            .ordinal()] >= maxEdgesPerNode
            || edgeCounts[edge.to()
                .ordinal()] >= maxEdgesPerNode) {
            return false;
        }
        edges.add(edge);
        edgeCounts[edge.from()
            .ordinal()]++;
        edgeCounts[edge.to()
            .ordinal()]++;
        return true;
    }

    private static boolean addRequiredEdge(Node from, Node to, List<Edge> edges) {
        if (from.bodyId() == to.bodyId()) return false;
        Edge edge = new Edge(from.bodyId(), to.bodyId());
        if (edges.contains(edge)) return false;
        edges.add(edge);
        return true;
    }
}
