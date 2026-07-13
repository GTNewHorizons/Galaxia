package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Comparator;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

/**
 * Scan constraints for asteroid-field discovery work.
 *
 * Satellites scan from one anchored body. The asteroid adapter validates that
 * the generic anchor is a minor body in the target belt, then turns it into the
 * belt-local node predicate used by asteroid discovery.
 */
public record AsteroidFieldScanContext(@Nonnull Predicate<AsteroidFieldNode> scope,
    @Nonnull Comparator<AsteroidFieldNode> order) {

    public AsteroidFieldScanContext {
        if (scope == null) throw new IllegalArgumentException("scan scope is required");
        if (order == null) throw new IllegalArgumentException("scan order is required");
    }

    public static AsteroidFieldScanContext from(@Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, @Nonnull CelestialObjectKey anchorKey, double radius) {
        if (beltId == null) throw new IllegalArgumentException("beltId is required");
        if (profile == null) throw new IllegalArgumentException("profile is required");
        if (anchorKey == null) throw new IllegalArgumentException("anchorKey is required");
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("scan radius must be finite and non-negative");
        }
        if (!anchorKey.isMinorBody()) {
            throw new IllegalArgumentException("asteroid scan scope requires a minor-body anchor");
        }

        MinorCelestialBodyId anchorId = anchorKey.minorBodyId();
        if (!anchorId.parentBodyId()
            .equals(beltId)) {
            throw new IllegalArgumentException("asteroid scan anchor parent must match belt id");
        }

        AsteroidFieldNode anchor = AsteroidFieldResolver.resolveNode(beltId, profile, anchorId.index());
        OrbitalMechanics.OrbitalState beltState = new OrbitalMechanics.OrbitalState(1.0, 0.0, 0.0, 0.0);
        OrbitalMechanics.OrbitalState center = AsteroidFieldOrbitResolver.resolveWorldState(profile, anchor, beltState);
        double radiusSquared = radius * radius;
        return new AsteroidFieldScanContext(node -> {
            // Compare squared distance in the same local reference frame used by
            // asteroid placement, independent from solar-system translation.
            OrbitalMechanics.OrbitalState asteroidState = AsteroidFieldOrbitResolver
                .resolveWorldState(profile, node, beltState);
            double dx = asteroidState.x() - center.x();
            double dy = asteroidState.y() - center.y();
            return dx * dx + dy * dy <= radiusSquared;
        }, AsteroidFieldScanOrder.discoveryOrder());
    }
}
