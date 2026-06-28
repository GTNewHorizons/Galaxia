package com.gtnewhorizons.galaxia.api;

import java.util.List;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataKey;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkCalculator;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkState;

/*
 * Read-only integration surface for other modules. Galaxia itself mutates satellite state through the asset/network
 * services; callers outside this package should use this class to ask what the current team network can provide.
 */
public final class GalaxiaSatelliteAPI {

    private GalaxiaSatelliteAPI() {}

    public record PendingData(CelestialObjectId bodyId, List<CelestialObjectId> destinationBodyIds,
        SatelliteDataKey key, long deciKb) {}

    public static int count(UUID teamId, CelestialObjectId bodyId, SatelliteKind kind) {
        return CelestialAssetStore.SERVER.satelliteCount(teamId, bodyId, kind);
    }

    public static long bandwidth(UUID teamId, CelestialObjectId bodyId) {
        return CelestialAssetStore.SERVER.satelliteBandwidth(teamId, bodyId);
    }

    public static double miningSpeedBonus(UUID teamId, CelestialObjectId bodyId) {
        return CelestialAssetStore.SERVER.satelliteMiningSpeedBonus(teamId, bodyId);
    }

    public static long localCapacityKbps(UUID teamId, CelestialObjectId bodyId) {
        return SatelliteNetworkService.current(teamId)
            .capacityKbps(bodyId);
    }

    public static long localUsedKbps(UUID teamId, CelestialObjectId bodyId) {
        return SatelliteNetworkService.current(teamId)
            .usedKbps(bodyId);
    }

    public static long pathCapacityKbps(UUID teamId, CelestialObjectId from, CelestialObjectId to) {
        SatelliteNetworkState state = SatelliteNetworkService.current(teamId);
        /*
         * Path capacity is the best available bottleneck between two bodies, not a sum of every possible route. That
         * matches the current transfer planner, which sends each produced buffer over one chosen route.
         */
        return SatelliteNetworkCalculator.widestPath(from, to, state)
            .capacityKbps();
    }

    public static List<PendingData> pendingData(UUID teamId, CelestialObjectId bodyId) {
        return SatelliteNetworkService.current(teamId)
            .pendingData(bodyId)
            .stream()
            .map(entry -> new PendingData(entry.bodyId(), entry.destinationBodyIds(), entry.key(), entry.deciKb()))
            .toList();
    }

    public static boolean canStartProcess(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey outputKey) {
        return SatelliteNetworkService.canStartProcess(teamId, bodyId, outputKey);
    }
}
