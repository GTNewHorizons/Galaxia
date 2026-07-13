package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.util.DeterministicHash;

final class AsteroidFieldDeterminism {

    private final long baseSeed;

    private AsteroidFieldDeterminism(long baseSeed) {
        this.baseSeed = baseSeed;
    }

    static AsteroidFieldDeterminism forNode(CelestialObjectId beltId, AsteroidFieldProfile profile, int index) {
        return new AsteroidFieldDeterminism(
            DeterministicHash.mix(
                beltId.name()
                    .hashCode(),
                profile.seedSalt(),
                profile.generationVersion(),
                index));
    }

    long seed(long... salts) {
        return DeterministicHash.mix(baseSeed, salts);
    }

    double unit(long... salts) {
        return DeterministicHash.unitDouble(seed(salts));
    }

    double degrees(long... salts) {
        return unit(salts) * 360.0;
    }

    static double normalizeDegrees(double value) {
        double normalized = value % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

}
