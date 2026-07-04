package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.progress.ProgressJobRunner;

/**
 * Describes one fact a team can uncover about a celestial object.
 *
 * Work items are intentionally data-shaped instead of callbacks so active scan
 * progress can be persisted and rebound to the current knowledge store after
 * loading.
 */
public interface CelestialDiscoveryWork extends ProgressJobRunner.Work {

    @Nonnull
    CelestialObjectKey targetKey();

    @Nonnull
    CelestialDiscoveryStep step();

    @Override
    default int durationTicks() {
        return step().durationTicks();
    }
}
