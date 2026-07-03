package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

/**
 * Builds predicates for satellite scan coverage in belt-local coordinates.
 */
public final class AsteroidFieldScanScope {

    private AsteroidFieldScanScope() {}

    public static Predicate<AsteroidFieldNode> withinRadius(@Nonnull AsteroidFieldProfile profile,
        @Nonnull OrbitalMechanics.OrbitalState beltState, @Nonnull OrbitalMechanics.OrbitalState centerState,
        double radius) {
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("scan radius must be finite and non-negative");
        }

        double radiusSquared = radius * radius;
        return node -> {
            // Compare squared distance in the same local reference frame used by
            // asteroid placement, independent from solar-system translation.
            OrbitalMechanics.OrbitalState asteroidState = OrbitalMechanics
                .resolveAsteroidFieldWorldState(profile, node, beltState);
            double dx = asteroidState.x() - centerState.x();
            double dy = asteroidState.y() - centerState.y();
            return dx * dx + dy * dy <= radiusSquared;
        };
    }
}
