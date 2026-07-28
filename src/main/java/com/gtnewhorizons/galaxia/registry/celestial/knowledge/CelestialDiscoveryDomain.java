package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

/**
 * Domain rules for one family of celestial discovery work.
 * <p>
 * Implementations own which anchors and scopes they answer for, pick the next
 * work item for a team, and apply the result through
 * {@link CelestialKnowledgeService}.
 */
public interface CelestialDiscoveryDomain {

    boolean ownsDiscoveryAnchor(@Nonnull CelestialObjectKey anchorKey);

    boolean ownsDiscoveryScope(@Nonnull CelestialDiscoveryScanScope scope);

    OptionalLong discoveryScopeRevision(@Nonnull CelestialObjectKey anchorKey);

    Optional<CelestialDiscoveryWork> nextDiscoveryWork(@Nonnull UUID teamId,
        @Nonnull CelestialDiscoveryScanScope scope);

    void completeDiscoveryWork(@Nonnull UUID teamId, @Nonnull CelestialDiscoveryScanScope scope,
        @Nonnull CelestialDiscoveryWork work);
}
