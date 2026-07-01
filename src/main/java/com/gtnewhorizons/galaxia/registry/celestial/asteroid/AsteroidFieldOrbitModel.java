package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

/**
 * Converts a node's polar belt-local coordinates into a world-space orbital
 * state using the belt container as the moving reference frame.
 */
public final class AsteroidFieldOrbitModel {

    private static final double MINIMUM_RADIUS_SQUARED = 1.0e-16;

    private AsteroidFieldOrbitModel() {}

    public static OrbitalMechanics.OrbitalState resolveWorldState(AsteroidFieldProfile profile, AsteroidFieldNode node,
        OrbitalMechanics.OrbitalState beltState) {
        Objects.requireNonNull(profile, "profile cannot be null");
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(beltState, "beltState cannot be null");

        double radius = resolveRadius(profile, node);
        double phaseRad = Math.atan2(beltState.y(), beltState.x()) + Math.toRadians(node.angleOffsetDeg());
        double angularVelocity = resolveAngularVelocity(beltState);
        // Asteroids ride the belt as rigid offsets: the belt supplies angular
        // velocity, while each node supplies its radius and angle within the band.
        double x = Math.cos(phaseRad) * radius;
        double y = Math.sin(phaseRad) * radius;
        double speed = angularVelocity * radius;
        double vx = -Math.sin(phaseRad) * speed;
        double vy = Math.cos(phaseRad) * speed;

        return new OrbitalMechanics.OrbitalState(x, y, vx, vy);
    }

    public static double resolveRadius(AsteroidFieldProfile profile, AsteroidFieldNode node) {
        Objects.requireNonNull(profile, "profile cannot be null");
        Objects.requireNonNull(node, "node cannot be null");
        return profile.innerOrbitalRadius()
            + (profile.outerOrbitalRadius() - profile.innerOrbitalRadius()) * node.orbitalDepth01();
    }

    private static double resolveAngularVelocity(OrbitalMechanics.OrbitalState beltState) {
        double radiusSquared = beltState.x() * beltState.x() + beltState.y() * beltState.y();
        if (radiusSquared <= MINIMUM_RADIUS_SQUARED) return 0.0;
        return (beltState.x() * beltState.vy() - beltState.y() * beltState.vx()) / radiusSquared;
    }
}
