package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

/**
 * Stable boundary of one discovery scan around an anchored celestial object.
 */
public record CelestialDiscoveryScanScope(@Nonnull CelestialObjectKey anchorKey, double radius, long revision) {

    public CelestialDiscoveryScanScope {
        if (anchorKey == null) throw new IllegalArgumentException("anchor key is required");
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("scan radius must be finite and non-negative");
        }
    }
}
