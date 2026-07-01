package com.gtnewhorizons.galaxia.registry.celestial.asteroid.content;

import java.util.List;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreProfile;

public final class GeneratedAsteroids {

    private static final double EARTH_RADIUS_TO_AU = 23481;

    private GeneratedAsteroids() {}

    public static void register(AsteroidContentBuilder builder) {
        builder.field(
            CelestialObjectId.FROZEN_BELT,
            field -> field.seedSalt(0xF20A3E11L)
                .generationVersion(1)
                .sizeCounts(6, 8, 12)
                .radialBand(2.15 * EARTH_RADIUS_TO_AU, 2.45 * EARTH_RADIUS_TO_AU)
                .satelliteScanRadius(0.12 * EARTH_RADIUS_TO_AU)
                .oreProfile(new AsteroidOreProfile("metallic", 3.0, List.of("ore.mix.iron")))
                .oreProfile(new AsteroidOreProfile("volatile_ice", 2.0, List.of("ore.mix.lapis")))
                .oreProfile(new AsteroidOreProfile("rare_crystal", 1.0, List.of("ore.mix.redstone"))));
    }
}
