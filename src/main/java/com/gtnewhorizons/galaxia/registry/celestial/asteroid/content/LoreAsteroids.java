package com.gtnewhorizons.galaxia.registry.celestial.asteroid.content;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;

public final class LoreAsteroids {

    private LoreAsteroids() {}

    public static void register(AsteroidContentBuilder builder) {
        builder.lore(
            "karnyx",
            lore -> lore.belt(CelestialObjectId.FROZEN_BELT)
                .slot(1)
                .name("Karnyx")
                .size(AsteroidSizeClass.LARGE)
                .position(184.5, 0.73)
                .oreProfile("rare_crystal")
                .detected()
                .oreProfileKnown());
    }
}
