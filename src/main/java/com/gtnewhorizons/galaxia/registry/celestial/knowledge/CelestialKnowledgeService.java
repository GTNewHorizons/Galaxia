package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeService;

/**
 * Server-side owner for team knowledge about celestial objects.
 *
 * Object-specific providers own their storage and rules; callers use this
 * service for shared discovery-state reads and lifecycle boundaries.
 */
public final class CelestialKnowledgeService {

    private static final List<CelestialKnowledgeProvider> KNOWLEDGE_PROVIDERS = List
        .of(new RegisteredCelestialDiscoveryProvider(), AsteroidFieldKnowledgeService.provider());

    private CelestialKnowledgeService() {}

    public static DiscoveryState discoveryState(@Nonnull UUID teamId, @Nonnull CelestialObjectKey key) {
        if (teamId == null) throw new IllegalArgumentException("team id is required");
        if (key == null) throw new IllegalArgumentException("celestial object key is required");
        return KNOWLEDGE_PROVIDERS.stream()
            .map(provider -> provider.discoveryState(teamId, key))
            .flatMap(Optional::stream)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No discovery provider for celestial object: " + key));
    }

    public static void clear() {
        KNOWLEDGE_PROVIDERS.forEach(CelestialKnowledgeProvider::clear);
    }
}
