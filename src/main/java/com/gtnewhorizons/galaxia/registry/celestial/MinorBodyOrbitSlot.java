package com.gtnewhorizons.galaxia.registry.celestial;

/**
 * Stable position of a minor body inside its parent orbital container.
 *
 * The angle is the offset from the container's current phase. The depth is a
 * normalized radial position, letting each container decide how [0, 1] maps to
 * its physical band.
 */
public record MinorBodyOrbitSlot(double angleOffsetDeg, double orbitalDepth01) {

    public MinorBodyOrbitSlot {
        if (!Double.isFinite(angleOffsetDeg) || angleOffsetDeg < 0.0 || angleOffsetDeg >= 360.0) {
            throw new IllegalArgumentException("angleOffsetDeg must be in [0, 360)");
        }
        if (!Double.isFinite(orbitalDepth01) || orbitalDepth01 < 0.0 || orbitalDepth01 > 1.0) {
            throw new IllegalArgumentException("orbitalDepth01 must be in [0, 1]");
        }
    }

    public double radiusBetween(double innerRadius, double outerRadius) {
        return innerRadius + (outerRadius - innerRadius) * orbitalDepth01;
    }
}
