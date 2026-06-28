package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

/*
 * Immutable server-derived snapshot synced to clients. It intentionally contains topology, current throughput, and
 * pending transfer summaries together so one packet can update lines, colours, and tooltips atomically.
 */
public record SatelliteNetworkState(UUID teamId, int revision, Map<CelestialObjectId, Body> bodies, List<Link> links,
    List<PendingData> pendingData) {

    public SatelliteNetworkState {
        bodies = Map.copyOf(bodies == null ? Map.of() : bodies);
        links = List.copyOf(links == null ? List.of() : links);
        pendingData = List.copyOf(pendingData == null ? List.of() : pendingData);
    }

    public SatelliteNetworkState(UUID teamId, int revision, Map<CelestialObjectId, Body> bodies, List<Link> links) {
        this(teamId, revision, bodies, links, List.of());
    }

    public static SatelliteNetworkState empty(UUID teamId, int revision) {
        return new SatelliteNetworkState(teamId, revision, Map.of(), List.of());
    }

    public Body body(CelestialObjectId bodyId) {
        return bodies.get(bodyId);
    }

    public long capacityKbps(CelestialObjectId bodyId) {
        Body body = bodies.get(bodyId);
        return body == null ? 0L : body.capacityKbps();
    }

    public long usedKbps(CelestialObjectId bodyId) {
        Body body = bodies.get(bodyId);
        return body == null ? 0L : body.usedKbps();
    }

    public List<PendingData> pendingData(CelestialObjectId bodyId) {
        return pendingData.stream()
            .filter(entry -> entry.bodyId() == bodyId)
            .toList();
    }

    public SatelliteNetworkState withPendingData(List<PendingData> pendingData) {
        return new SatelliteNetworkState(teamId, revision, bodies, links, pendingData);
    }

    /*
     * Body usage is local satellite load. It is not the sum of adjacent link colours; the planner clamps all paths that
     * pass through this body against this one local capacity pool.
     */
    public record Body(CelestialObjectId bodyId, long capacityKbps, long usedKbps) {

        public Body {
            capacityKbps = Math.max(0L, capacityKbps);
            usedKbps = Math.max(0L, usedKbps);
        }
    }

    /*
     * Links are stored in a normalized undirected order for stable equality and sync diffs, but keep directional usage
     * so the renderer can show packets moving in the same direction as actual data.
     */
    public record Link(CelestialObjectId from, CelestialObjectId to, long capacityKbps, long usedKbps,
        long forwardUsedKbps, long reverseUsedKbps) {

        public Link {
            SatelliteNetworkGraph.Edge normalized = new SatelliteNetworkGraph.Edge(from, to);
            boolean reversed = normalized.from() != from;
            from = normalized.from();
            to = normalized.to();
            capacityKbps = Math.max(0L, capacityKbps);
            usedKbps = Math.max(0L, usedKbps);
            forwardUsedKbps = Math.max(0L, forwardUsedKbps);
            reverseUsedKbps = Math.max(0L, reverseUsedKbps);
            if (reversed) {
                long swap = forwardUsedKbps;
                forwardUsedKbps = reverseUsedKbps;
                reverseUsedKbps = swap;
            }
        }

        public SatelliteNetworkGraph.Edge asEdge() {
            return new SatelliteNetworkGraph.Edge(from, to);
        }

        public long usedKbps(CelestialObjectId source, CelestialObjectId destination) {
            if (source == from && destination == to) return forwardUsedKbps;
            if (source == to && destination == from) return reverseUsedKbps;
            return 0L;
        }
    }

    public record PendingData(CelestialObjectId bodyId, List<CelestialObjectId> destinationBodyIds,
        SatelliteDataKey key, long deciKb) {

        public PendingData {
            destinationBodyIds = List.copyOf(destinationBodyIds == null ? List.of() : destinationBodyIds);
            deciKb = Math.max(0L, deciKb);
        }
    }
}
