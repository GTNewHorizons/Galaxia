package com.gtnewhorizons.galaxia.registry.celestial.asteroid.content;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodeKind;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AuthoredAsteroidDefinition;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;

public final class LoreAsteroids {

    private LoreAsteroids() {}

    public static void register(AsteroidFieldProfile.Builder field) {
        field.authoredAsteroid(
            new AuthoredAsteroidDefinition(
                1,
                AsteroidNodeKind.LORE,
                "karnyx",
                "Karnyx",
                AsteroidSizeClass.LARGE,
                DiscoveryState.DISCOVERED,
                CelestialResourceKnowledgeState.PROFILE,
                184.5,
                0.73,
                "rare_crystal",
                null));
    }
}
