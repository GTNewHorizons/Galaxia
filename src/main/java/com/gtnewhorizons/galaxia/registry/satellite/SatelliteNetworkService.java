package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalParams;

public final class SatelliteNetworkService {

    private static final Map<UUID, SatelliteNetworkState> STATES = new HashMap<>();

    private SatelliteNetworkService() {}

    public static SatelliteNetworkState current(UUID teamId) {
        return STATES.getOrDefault(teamId, SatelliteNetworkState.empty(teamId, 0));
    }

    public static SatelliteNetworkState rebuild(UUID teamId, double orbitalTime) {
        List<SatelliteNetworkGraph.Node> nodes = buildNodes(orbitalTime);
        Map<CelestialObjectId, Long> capacityByBody = new HashMap<>();
        for (SatelliteNetworkGraph.Node node : nodes) {
            long capacity = CelestialAssetStore.SERVER.satelliteBandwidth(teamId, node.bodyId());
            if (capacity > 0L) capacityByBody.put(node.bodyId(), capacity);
        }
        return rebuild(teamId, nodes, capacityByBody, Map.of());
    }

    public static SatelliteNetworkState rebuild(UUID teamId, List<SatelliteNetworkGraph.Node> nodes,
        Map<CelestialObjectId, Long> capacityByBody, Map<SatelliteNetworkGraph.Edge, Long> usedByEdge) {
        SatelliteNetworkState previous = current(teamId);
        SatelliteNetworkState next = SatelliteNetworkCalculator
            .forTeam(teamId, previous.revision() + 1, nodes, capacityByBody, usedByEdge);
        if (sameContent(previous, next)) return previous;
        STATES.put(teamId, next);
        return next;
    }

    public static void clear() {
        STATES.clear();
    }

    private static List<SatelliteNetworkGraph.Node> buildNodes(double orbitalTime) {
        CelestialObject root = GalaxiaCelestialAPI.root();
        return GalaxiaCelestialAPI.getAllBodies()
            .values()
            .stream()
            .filter(body -> body.objectClass() != CelestialObject.Class.GALAXY)
            .filter(body -> body.objectClass() != CelestialObject.Class.STAR)
            .map(body -> nodeFor(root, body, orbitalTime))
            .toList();
    }

    private static SatelliteNetworkGraph.Node nodeFor(CelestialObject root, CelestialObject body, double orbitalTime) {
        OrbitalMechanics.OrbitalState state = OrbitalMechanics.resolveWorldState(root, body, orbitalTime);
        return new SatelliteNetworkGraph.Node(
            body.id(),
            body.parentId(),
            orbitalOrder(body),
            state.x(),
            state.y(),
            Math.max(0.0D, body.spriteSize()));
    }

    private static double orbitalOrder(CelestialObject body) {
        OrbitalParams params = body.orbitalParams();
        return params == null ? body.id()
            .ordinal() : params.semiMajorAxis();
    }

    private static boolean sameContent(SatelliteNetworkState previous, SatelliteNetworkState next) {
        return previous.bodies()
            .equals(next.bodies())
            && previous.links()
                .equals(next.links());
    }
}
