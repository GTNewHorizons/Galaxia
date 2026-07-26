package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

/**
 * One discoverable fact a scan can uncover about a celestial object.
 * <p>
 * TLDR: data-shaped work item ({@code Key + step}) so scan progress persists
 * and rebinds after load without feature-specific work types.
 */
public record CelestialDiscoveryWork(@Nonnull CelestialObjectKey targetKey, @Nonnull CelestialDiscoveryStep step) {

    public CelestialDiscoveryWork {
        if (targetKey == null) throw new IllegalArgumentException("target key is required");
        if (step == null) throw new IllegalArgumentException("discovery step is required");
    }

    public int durationTicks() {
        return step.durationTicks();
    }
}
