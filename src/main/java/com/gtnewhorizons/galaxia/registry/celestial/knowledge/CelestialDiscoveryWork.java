package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

/**
 * Describes one fact a team can uncover about a celestial object.
 *
 * Work items are intentionally data-shaped instead of callbacks so active scan
 * progress can be persisted and rebound to the current knowledge store after
 * loading.
 */
public interface CelestialDiscoveryWork {

    @Nonnull
    CelestialObjectKey targetKey();

    @Nonnull
    CelestialDiscoveryStep step();

    default int durationTicks() {
        return step().durationTicks();
    }
}
