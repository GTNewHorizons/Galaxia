package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.util.DeterministicHash;

final class AsteroidFieldDeterminism {

    private AsteroidFieldDeterminism() {}

    static long nodeSeed(CelestialObjectId beltId, AsteroidFieldProfile profile, int index) {
        return mix(
            beltId.name()
                .hashCode(),
            profile.seedSalt(),
            profile.generationVersion(),
            index);
    }

    static double normalizeDegrees(double value) {
        double normalized = value % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    static double unitDouble(long value) {
        return DeterministicHash.unitDouble(value);
    }

    static long mix(long first, long... rest) {
        return DeterministicHash.mix(first, rest);
    }
}
