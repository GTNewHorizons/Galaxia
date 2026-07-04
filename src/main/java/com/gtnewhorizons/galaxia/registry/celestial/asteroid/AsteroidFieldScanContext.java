package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Comparator;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

/**
 * Scan constraints for asteroid-field discovery work.
 *
 * Satellites scan from one anchor asteroid, so the scope limits which nodes are
 * reachable and the order keeps each pass deterministic.
 */
public record AsteroidFieldScanContext(@Nonnull Predicate<AsteroidFieldNode> scope,
    @Nonnull Comparator<AsteroidFieldNode> order) {

    public AsteroidFieldScanContext {
        if (scope == null) throw new IllegalArgumentException("scan scope is required");
        if (order == null) throw new IllegalArgumentException("scan order is required");
    }
}
