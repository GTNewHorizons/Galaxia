package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Objects;
import java.util.function.Predicate;

import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

public final class AsteroidFieldScanScope {

    private AsteroidFieldScanScope() {}

    public static Predicate<AsteroidFieldNode> withinRadius(AsteroidFieldProfile profile,
        OrbitalMechanics.OrbitalState beltState, OrbitalMechanics.OrbitalState centerState, double radius) {
        Objects.requireNonNull(profile, "profile cannot be null");
        Objects.requireNonNull(beltState, "beltState cannot be null");
        Objects.requireNonNull(centerState, "centerState cannot be null");
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("scan radius must be finite and non-negative");
        }

        double radiusSquared = radius * radius;
        return node -> {
            OrbitalMechanics.OrbitalState asteroidState = AsteroidFieldOrbitModel
                .resolveWorldState(profile, node, beltState);
            double dx = asteroidState.x() - centerState.x();
            double dy = asteroidState.y() - centerState.y();
            return dx * dx + dy * dy <= radiusSquared;
        };
    }
}
