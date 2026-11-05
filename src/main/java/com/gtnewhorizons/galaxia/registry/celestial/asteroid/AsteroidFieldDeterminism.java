package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.util.DeterministicHash;

final class AsteroidFieldDeterminism {

    private static final double FULL_CIRCLE_DEGREES = 360.0;
    private static final double ZERO_DEGREES = 0.0;
    private static final long FIELD_LAYOUT_SALT = 0x004C41594F55544CL;

    private final long baseSeed;

    private AsteroidFieldDeterminism(long baseSeed) {
        this.baseSeed = baseSeed;
    }

    static AsteroidFieldDeterminism forField(CelestialObjectId beltId, AsteroidFieldProfile profile) {
        return new AsteroidFieldDeterminism(
            DeterministicHash.mix(
                beltId.name()
                    .hashCode(),
                profile.seedSalt(),
                FIELD_LAYOUT_SALT));
    }

    static AsteroidFieldDeterminism forNode(CelestialObjectId beltId, AsteroidFieldProfile profile, int index) {
        return new AsteroidFieldDeterminism(
            DeterministicHash.mix(
                beltId.name()
                    .hashCode(),
                profile.seedSalt(),
                index));
    }

    long seed(long... salts) {
        return DeterministicHash.mix(baseSeed, salts);
    }

    double unit(long... salts) {
        return DeterministicHash.unitDouble(seed(salts));
    }

    double degrees(long... salts) {
        return unit(salts) * FULL_CIRCLE_DEGREES;
    }

    static double normalizeDegrees(double value) {
        double normalized = value % FULL_CIRCLE_DEGREES;
        return normalized < ZERO_DEGREES ? normalized + FULL_CIRCLE_DEGREES : normalized;
    }

}
