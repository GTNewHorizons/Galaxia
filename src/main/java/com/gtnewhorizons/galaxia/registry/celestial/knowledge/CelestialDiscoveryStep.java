package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import javax.annotation.Nonnull;

/**
 * Stable discovery tier/type, such as asteroid existence or ore composition.
 */
public interface CelestialDiscoveryStep {

    @Nonnull
    String id();

    int durationTicks();
}
