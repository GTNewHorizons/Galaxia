package com.gtnewhorizons.galaxia.registry.celestial.asteroid.content;

import java.util.Map;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;

public final class AsteroidContentRegistry {

    private static final Map<CelestialObjectId, AsteroidFieldProfile> PROFILES = buildProfiles();

    private AsteroidContentRegistry() {}

    public static AsteroidFieldProfile profile(@Nonnull CelestialObjectId beltId) {
        AsteroidFieldProfile profile = PROFILES.get(beltId);
        if (profile == null) throw new IllegalStateException("No asteroid field content registered for " + beltId);
        return profile;
    }

    public static Map<CelestialObjectId, AsteroidFieldProfile> profiles() {
        return PROFILES;
    }

    private static Map<CelestialObjectId, AsteroidFieldProfile> buildProfiles() {
        AsteroidContentBuilder builder = new AsteroidContentBuilder();
        // Generated fields provide the base profile first. Authored lore/unique
        // asteroids then overlay fixed node presets into those fields.
        GeneratedAsteroids.register(builder);
        LoreAsteroids.register(builder);
        UniqueAsteroids.register(builder);
        return builder.buildProfiles();
    }
}
