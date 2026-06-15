package com.gtnewhorizons.galaxia.api;

import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

public final class GalaxiaSatelliteAPI {

    private GalaxiaSatelliteAPI() {}

    public static int count(UUID teamId, CelestialObjectId bodyId, SatelliteKind kind) {
        return CelestialAssetStore.SERVER.satelliteCount(teamId, bodyId, kind);
    }

    public static long bandwidth(UUID teamId, CelestialObjectId bodyId) {
        return CelestialAssetStore.SERVER.satelliteBandwidth(teamId, bodyId);
    }

    public static double miningSpeedBonus(UUID teamId, CelestialObjectId bodyId) {
        return CelestialAssetStore.SERVER.satelliteMiningSpeedBonus(teamId, bodyId);
    }
}
