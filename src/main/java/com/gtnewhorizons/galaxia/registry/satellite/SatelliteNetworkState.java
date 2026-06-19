package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public record SatelliteNetworkState(UUID teamId, int revision, Map<CelestialObjectId, Body> bodies, List<Link> links) {

    public SatelliteNetworkState {
        bodies = Map.copyOf(bodies == null ? Map.of() : bodies);
        links = List.copyOf(links == null ? List.of() : links);
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

    public record Body(CelestialObjectId bodyId, long capacityKbps, long usedKbps) {

        public Body {
            capacityKbps = Math.max(0L, capacityKbps);
            usedKbps = Math.max(0L, usedKbps);
        }
    }

    public record Link(CelestialObjectId from, CelestialObjectId to, long capacityKbps, long usedKbps) {

        public Link {
            SatelliteNetworkGraph.Edge normalized = new SatelliteNetworkGraph.Edge(from, to);
            from = normalized.from();
            to = normalized.to();
            capacityKbps = Math.max(0L, capacityKbps);
            usedKbps = Math.max(0L, usedKbps);
        }

        public SatelliteNetworkGraph.Edge asEdge() {
            return new SatelliteNetworkGraph.Edge(from, to);
        }
    }
}
