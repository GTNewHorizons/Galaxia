package com.gtnewhorizons.galaxia.api;

import java.util.List;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
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

    public record PendingData(CelestialObjectKey bodyKey, List<CelestialObjectKey> destinationBodyKeys,
        SatelliteDataKey key, long deciKb) {}

    public static int count(UUID teamId, CelestialObjectKey bodyKey, SatelliteKind kind) {
        return CelestialAssetStore.SERVER.satelliteCount(teamId, bodyKey, kind);
    }

    public static int count(UUID teamId, CelestialObjectId bodyId, SatelliteKind kind) {
        return count(teamId, CelestialObjectKey.registered(bodyId), kind);
    }

    public static long bandwidth(UUID teamId, CelestialObjectKey bodyKey) {
        return CelestialAssetStore.SERVER.satelliteBandwidth(teamId, bodyKey);
    }

    public static long bandwidth(UUID teamId, CelestialObjectId bodyId) {
        return bandwidth(teamId, CelestialObjectKey.registered(bodyId));
    }

    public static double miningSpeedBonus(UUID teamId, CelestialObjectKey bodyKey) {
        return CelestialAssetStore.SERVER.satelliteMiningSpeedBonus(teamId, bodyKey);
    }

    public static double miningSpeedBonus(UUID teamId, CelestialObjectId bodyId) {
        return miningSpeedBonus(teamId, CelestialObjectKey.registered(bodyId));
    }

    public static long localCapacityKbps(UUID teamId, CelestialObjectKey bodyKey) {
        return SatelliteNetworkService.current(teamId)
            .capacityKbps(bodyKey);
    }

    public static long localCapacityKbps(UUID teamId, CelestialObjectId bodyId) {
        return localCapacityKbps(teamId, CelestialObjectKey.registered(bodyId));
    }

    public static long localUsedKbps(UUID teamId, CelestialObjectKey bodyKey) {
        return SatelliteNetworkService.current(teamId)
            .usedKbps(bodyKey);
    }

    public static long localUsedKbps(UUID teamId, CelestialObjectId bodyId) {
        return localUsedKbps(teamId, CelestialObjectKey.registered(bodyId));
    }

    public static long pathCapacityKbps(UUID teamId, CelestialObjectKey from, CelestialObjectKey to) {
        SatelliteNetworkState state = SatelliteNetworkService.current(teamId);
        /*
         * Path capacity is the best available bottleneck between two bodies, not a sum of every possible route. That
         * matches the current transfer planner, which sends each produced buffer over one chosen route.
         */
        return SatelliteNetworkCalculator.widestPath(from, to, state)
            .capacityKbps();
    }

    public static long pathCapacityKbps(UUID teamId, CelestialObjectId from, CelestialObjectId to) {
        return pathCapacityKbps(teamId, CelestialObjectKey.registered(from), CelestialObjectKey.registered(to));
    }

    public static List<PendingData> pendingData(UUID teamId, CelestialObjectKey bodyKey) {
        return SatelliteNetworkService.current(teamId)
            .pendingData(bodyKey)
            .stream()
            .map(entry -> new PendingData(entry.bodyKey(), entry.destinationBodyKeys(), entry.key(), entry.deciKb()))
            .toList();
    }

    public static List<PendingData> pendingData(UUID teamId, CelestialObjectId bodyId) {
        return pendingData(teamId, CelestialObjectKey.registered(bodyId));
    }

    public static boolean canStartProcess(UUID teamId, CelestialObjectKey bodyKey, SatelliteDataKey outputKey) {
        return SatelliteNetworkService.canStartProcess(teamId, bodyKey, outputKey);
    }

    public static boolean canStartProcess(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey outputKey) {
        return canStartProcess(teamId, CelestialObjectKey.registered(bodyId), outputKey);
    }
}
