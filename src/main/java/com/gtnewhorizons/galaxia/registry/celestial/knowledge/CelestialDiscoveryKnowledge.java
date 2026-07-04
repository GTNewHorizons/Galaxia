package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.Optional;

import javax.annotation.Nonnull;

/**
 * Mutable team knowledge that can select and apply progressive discovery work.
 *
 * The context type lets a domain add scan constraints such as an anchor radius or
 * ordering without making the scan runner depend on that domain.
 */
public interface CelestialDiscoveryKnowledge<C> {

    Optional<CelestialDiscoveryWork> nextDiscoveryWork(@Nonnull C context);

    void revealDiscovery(@Nonnull CelestialDiscoveryWork work, @Nonnull C context);
}
