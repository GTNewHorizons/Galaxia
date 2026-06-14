package com.gtnewhorizons.galaxia.api;

import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.satellite.PlanetarySatelliteStore;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

public final class GalaxiaSatelliteAPI {

    private GalaxiaSatelliteAPI() {}

    public static int count(UUID teamId, CelestialObjectId bodyId, SatelliteKind kind) {
        return PlanetarySatelliteStore.SERVER.count(teamId, bodyId, kind);
    }

    public static long bandwidth(UUID teamId, CelestialObjectId bodyId) {
        return PlanetarySatelliteStore.SERVER.bandwidth(teamId, bodyId);
    }

    public static double miningSpeedBonus(UUID teamId, CelestialObjectId bodyId) {
        return PlanetarySatelliteStore.SERVER.miningSpeedBonus(teamId, bodyId);
    }
}
