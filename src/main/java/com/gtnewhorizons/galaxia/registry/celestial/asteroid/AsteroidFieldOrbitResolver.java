package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.MinorBodyOrbitSlot;
import com.gtnewhorizons.galaxia.registry.orbital.MinorBodyOrbitResolver;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

public final class AsteroidFieldOrbitResolver implements MinorBodyOrbitResolver {

    public static final AsteroidFieldOrbitResolver INSTANCE = new AsteroidFieldOrbitResolver();

    private static final double MINIMUM_RADIUS = 1e-8;

    private AsteroidFieldOrbitResolver() {}

    @Override
    public @Nullable OrbitalMechanics.OrbitalState resolveWorldState(CelestialObject parent, CelestialObject child,
        OrbitalMechanics.OrbitalState parentState) {
        AsteroidFieldProfile profile = parent.properties()
            .asteroidFieldProfile();
        if (profile == null) return null;

        MinorCelestialBodyId minorId = child.key()
            .minorBodyId();
        CelestialObjectId parentId = parent.key()
            .registeredBodyId();
        AsteroidFieldNode node = AsteroidFieldResolver.resolveNode(parentId, profile, minorId.index());
        return resolveWorldState(profile, node, parentState);
    }

    public static OrbitalMechanics.OrbitalState resolveWorldState(AsteroidFieldProfile profile, AsteroidFieldNode node,
        OrbitalMechanics.OrbitalState beltState) {
        MinorBodyOrbitSlot slot = node.orbitSlot();
        double radius = resolveRadius(profile, node);
        double phaseRad = Math.atan2(beltState.y(), beltState.x()) + Math.toRadians(slot.angleOffsetDeg());
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
        return node.orbitSlot()
            .radiusBetween(profile.innerOrbitalRadius(), profile.outerOrbitalRadius());
    }

    /** In-band separation between two nodes, ignoring the belt phase they share. */
    public static double separation(AsteroidFieldProfile profile, AsteroidFieldNode first, AsteroidFieldNode second) {
        double firstRadius = resolveRadius(profile, first);
        double firstAngle = Math.toRadians(first.angleOffsetDeg());
        double secondRadius = resolveRadius(profile, second);
        double secondAngle = Math.toRadians(second.angleOffsetDeg());
        double dx = Math.cos(firstAngle) * firstRadius - Math.cos(secondAngle) * secondRadius;
        double dy = Math.sin(firstAngle) * firstRadius - Math.sin(secondAngle) * secondRadius;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double resolveAngularVelocity(OrbitalMechanics.OrbitalState state) {
        double radiusSquared = state.x() * state.x() + state.y() * state.y();
        if (radiusSquared <= MINIMUM_RADIUS * MINIMUM_RADIUS) return 0.0;
        return (state.x() * state.vy() - state.y() * state.vx()) / radiusSquared;
    }
}
