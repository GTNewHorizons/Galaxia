package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

/*
 * Immutable server-derived snapshot synced to clients. It intentionally contains topology, current throughput, and
 * pending transfer summaries together so one packet can update lines, colours, and tooltips atomically.
 */
public record SatelliteNetworkState(UUID teamId, int revision, Map<CelestialObjectKey, Body> bodies, List<Link> links,
    List<PendingData> pendingData) {

    public SatelliteNetworkState {
        bodies = Map.copyOf(bodies == null ? Map.of() : bodies);
        links = List.copyOf(links == null ? List.of() : links);
        pendingData = List.copyOf(pendingData == null ? List.of() : pendingData);
    }

    public SatelliteNetworkState(UUID teamId, int revision, Map<CelestialObjectKey, Body> bodies, List<Link> links) {
        this(teamId, revision, bodies, links, List.of());
    }

    public static SatelliteNetworkState empty(UUID teamId, int revision) {
        return new SatelliteNetworkState(teamId, revision, Map.of(), List.of());
    }

    public Body body(CelestialObjectKey bodyKey) {
        return bodies.get(bodyKey);
    }

    public Body body(CelestialObjectId bodyId) {
        return body(CelestialObjectKey.registered(bodyId));
    }

    public long capacityKbps(CelestialObjectKey bodyKey) {
        Body body = bodies.get(bodyKey);
        return body == null ? 0L : body.capacityKbps();
    }

    public long capacityKbps(CelestialObjectId bodyId) {
        return capacityKbps(CelestialObjectKey.registered(bodyId));
    }

    public long usedKbps(CelestialObjectKey bodyKey) {
        Body body = bodies.get(bodyKey);
        return body == null ? 0L : body.usedKbps();
    }

    public long usedKbps(CelestialObjectId bodyId) {
        return usedKbps(CelestialObjectKey.registered(bodyId));
    }

    public List<PendingData> pendingData(CelestialObjectKey bodyKey) {
        return pendingData.stream()
            .filter(
                entry -> entry.bodyKey()
                    .equals(bodyKey))
            .toList();
    }

    public List<PendingData> pendingData(CelestialObjectId bodyId) {
        return pendingData(CelestialObjectKey.registered(bodyId));
    }

    public SatelliteNetworkState withPendingData(List<PendingData> pendingData) {
        return new SatelliteNetworkState(teamId, revision, bodies, links, pendingData);
    }

    /*
     * Body usage is local satellite load. It is not the sum of adjacent link colours; the planner clamps all paths that
     * pass through this body against this one local capacity pool.
     */
    public record Body(CelestialObjectKey bodyKey, long capacityKbps, long usedKbps) {

        public Body {
            if (bodyKey == null) {
                throw new IllegalArgumentException("bodyKey cannot be null");
            }
            capacityKbps = Math.max(0L, capacityKbps);
            usedKbps = Math.max(0L, usedKbps);
        }

        public Body(CelestialObjectId bodyId, long capacityKbps, long usedKbps) {
            this(CelestialObjectKey.registered(bodyId), capacityKbps, usedKbps);
        }

        public CelestialObjectId bodyId() {
            return bodyKey.requireRegisteredBodyId();
        }
    }

    /*
     * Links are stored in a normalized undirected order for stable equality and sync diffs, but keep directional usage
     * so the renderer can show packets moving in the same direction as actual data.
     */
    public record Link(CelestialObjectKey from, CelestialObjectKey to, long capacityKbps, long usedKbps,
        long forwardUsedKbps, long reverseUsedKbps) {

        public Link {
            SatelliteNetworkGraph.Edge normalized = new SatelliteNetworkGraph.Edge(from, to);
            boolean reversed = !normalized.from()
                .equals(from);
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

        public Link(CelestialObjectId from, CelestialObjectId to, long capacityKbps, long usedKbps,
            long forwardUsedKbps, long reverseUsedKbps) {
            this(
                CelestialObjectKey.registered(from),
                CelestialObjectKey.registered(to),
                capacityKbps,
                usedKbps,
                forwardUsedKbps,
                reverseUsedKbps);
        }

        public long usedKbps(CelestialObjectKey source, CelestialObjectKey destination) {
            if (from.equals(source) && to.equals(destination)) return forwardUsedKbps;
            if (to.equals(source) && from.equals(destination)) return reverseUsedKbps;
            return 0L;
        }

        public long usedKbps(CelestialObjectId source, CelestialObjectId destination) {
            return usedKbps(CelestialObjectKey.registered(source), CelestialObjectKey.registered(destination));
        }

        public CelestialObjectId fromId() {
            return from.requireRegisteredBodyId();
        }

        public CelestialObjectId toId() {
            return to.requireRegisteredBodyId();
        }
    }

    public record PendingData(CelestialObjectKey bodyKey, List<CelestialObjectKey> destinationBodyKeys,
        SatelliteDataKey key, long deciKb) {

        public PendingData {
            if (bodyKey == null) {
                throw new IllegalArgumentException("bodyKey cannot be null");
            }
            destinationBodyKeys = List.copyOf(destinationBodyKeys == null ? List.of() : destinationBodyKeys);
            deciKb = Math.max(0L, deciKb);
        }

        public PendingData(CelestialObjectId bodyId, List<CelestialObjectId> destinationBodyIds, SatelliteDataKey key,
            long deciKb) {
            this(
                CelestialObjectKey.registered(bodyId),
                destinationBodyIds == null ? List.of()
                    : destinationBodyIds.stream()
                        .map(CelestialObjectKey::registered)
                        .toList(),
                key,
                deciKb);
        }

        public CelestialObjectId bodyId() {
            return bodyKey.requireRegisteredBodyId();
        }

        public List<CelestialObjectId> destinationBodyIds() {
            return destinationBodyKeys.stream()
                .map(CelestialObjectKey::requireRegisteredBodyId)
                .toList();
        }
    }
}
