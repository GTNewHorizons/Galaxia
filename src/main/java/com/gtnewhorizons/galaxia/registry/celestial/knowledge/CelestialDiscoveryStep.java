package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import javax.annotation.Nonnull;

/**
 * Stable discovery tier/type for progressive scanning or research work.
 */
public interface CelestialDiscoveryStep {

    @Nonnull
    String id();

    int durationTicks();
}
