package com.gtnewhorizons.galaxia.registry.celestial.asteroid.content;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreProfiles;

public final class GeneratedAsteroids {

    private static final double EARTH_RADIUS_TO_AU = 23481;

    private GeneratedAsteroids() {}

    public static void register(AsteroidFieldProfile.Builder field) {
        field.seedSalt(0xF20A3E11L)
            .generationVersion(1)
            .sizeCounts(6, 8, 12)
            .radialBand(2.15 * EARTH_RADIUS_TO_AU, 2.45 * EARTH_RADIUS_TO_AU)
            .placementConnectionRadius(0.12 * EARTH_RADIUS_TO_AU)
            .oreProfile(AsteroidOreProfiles.METALLIC, 3.0)
            .oreProfile(AsteroidOreProfiles.VOLATILE_ICE, 2.0)
            .oreProfile(AsteroidOreProfiles.RARE_CRYSTAL, 1.0);
    }
}
