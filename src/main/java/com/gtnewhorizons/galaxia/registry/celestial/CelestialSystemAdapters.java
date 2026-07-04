package com.gtnewhorizons.galaxia.registry.celestial;

import com.gtnewhorizons.galaxia.core.network.AsteroidKnowledgeSyncAdapter;
import com.gtnewhorizons.galaxia.core.network.CelestialKnowledgeSyncRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeService;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitResolver;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

public final class CelestialSystemAdapters {

    private CelestialSystemAdapters() {}

    public static void register() {
        CelestialKnowledgeService.registerProvider(AsteroidFieldKnowledgeService.provider());
        OrbitalMechanics.registerMinorBodyResolver(AsteroidFieldOrbitResolver.INSTANCE);
        CelestialKnowledgeSyncRegistry.register(AsteroidKnowledgeSyncAdapter.INSTANCE);
    }
}
