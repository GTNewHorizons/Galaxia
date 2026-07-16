package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

/**
 * Server-side owner for one family of team knowledge.
 */
public interface CelestialKnowledgeProvider {

    /**
     * Returns {@link Optional#empty()} only when another provider owns the key.
     * If a provider owns the key but cannot resolve it, it should fail loudly.
     */
    Optional<DiscoveryState> discoveryState(@Nonnull UUID teamId, @Nonnull CelestialObjectKey key);

}
