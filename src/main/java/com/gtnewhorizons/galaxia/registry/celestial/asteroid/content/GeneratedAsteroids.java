package com.gtnewhorizons.galaxia.registry.celestial.asteroid.content;

import java.util.List;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreProfile;

public final class GeneratedAsteroids {

    private static final double EARTH_RADIUS_TO_AU = 23481;
    private static final AsteroidOreProfile METALLIC = new AsteroidOreProfile("metallic", List.of("ore.mix.iron"));
    private static final AsteroidOreProfile VOLATILE_ICE = new AsteroidOreProfile(
        "volatile_ice",
        List.of("ore.mix.lapis"));
    private static final AsteroidOreProfile RARE_CRYSTAL = new AsteroidOreProfile(
        "rare_crystal",
        List.of("ore.mix.redstone"));

    private GeneratedAsteroids() {}

    public static void register(AsteroidFieldProfile.Builder field) {
        field.seedSalt(0xF20A3E11L)
            .sizeCounts(40, 80, 200)
            .radialBand(2.15 * EARTH_RADIUS_TO_AU, 2.45 * EARTH_RADIUS_TO_AU)
            .placementConnectionRadius(0.12 * EARTH_RADIUS_TO_AU)
            .oreProfile(METALLIC, 3.0)
            .oreProfile(VOLATILE_ICE, 2.0)
            .oreProfile(RARE_CRYSTAL, 1.0);
    }
}
