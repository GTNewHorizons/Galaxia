package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

final class AsteroidGeneratedSlotAllocator {

    private AsteroidGeneratedSlotAllocator() {}

    static AsteroidSizeClass generatedSizeClass(AsteroidFieldProfile profile, int index) {
        int ordinal = AsteroidSlotRanges.generatedOrdinal(index);
        if (ordinal >= profile.totalNodes()) return AsteroidSizeClass.SMALL;
        return generatedSizeClassAtOrdinal(profile, ordinal);
    }

    static AsteroidSizeClass generatedSizeClassAtOrdinal(AsteroidFieldProfile profile, int ordinal) {
        int total = profile.totalNodes();
        int[] counts = { profile.largeCount(), profile.mediumCount(), profile.smallCount() };
        int[] emitted = new int[counts.length];
        int[] score = new int[counts.length];
        int selected = 0;
        for (int slot = 0; slot <= ordinal; slot++) {
            selected = nextInterleavedSizeClass(counts, emitted, score, total);
            emitted[selected]++;
            score[selected] -= total;
        }
        return switch (selected) {
            case 0 -> AsteroidSizeClass.LARGE;
            case 1 -> AsteroidSizeClass.MEDIUM;
            default -> AsteroidSizeClass.SMALL;
        };
    }

    static double generatedAngleOffsetDeg(AsteroidFieldProfile profile, int index, long baseSeed,
        AsteroidSizeClass sizeClass) {
        int ordinal = AsteroidSlotRanges.generatedOrdinal(index);
        int classOrdinal = generatedSizeClassOrdinal(profile, ordinal, sizeClass);
        int classCount = generatedSizeClassCount(profile, sizeClass);
        if (classCount <= 0)
            return AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 1L)) * 360.0;

        double sectorWidth = 360.0 / classCount;
        double phase = AsteroidFieldDeterminism.unitDouble(
            AsteroidFieldDeterminism.mix(
                profile.seedSalt(),
                profile.generationVersion(),
                sizeClass.name()
                    .hashCode(),
                19L))
            * sectorWidth;
        double jitterScale = sizeClass == AsteroidSizeClass.LARGE ? 0.18 : 0.55;
        double jitter = (AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 1L)) - 0.5)
            * sectorWidth
            * jitterScale;
        return AsteroidFieldDeterminism.normalizeDegrees((classOrdinal + 0.5) * sectorWidth + phase + jitter);
    }

    private static int nextInterleavedSizeClass(int[] counts, int[] emitted, int[] score, int total) {
        int selected = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int sizeClass = 0; sizeClass < counts.length; sizeClass++) {
            if (emitted[sizeClass] >= counts[sizeClass]) continue;
            score[sizeClass] += counts[sizeClass];
            if (score[sizeClass] > bestScore || score[sizeClass] == bestScore && counts[sizeClass] > counts[selected]) {
                selected = sizeClass;
                bestScore = score[sizeClass];
            }
        }
        if (selected < 0) throw new IllegalStateException("generated asteroid size class allocation exhausted");
        return selected;
    }

    private static int generatedSizeClassOrdinal(AsteroidFieldProfile profile, int ordinal,
        AsteroidSizeClass sizeClass) {
        int count = 0;
        int boundedOrdinal = Math.min(ordinal, profile.totalNodes());
        for (int previous = 0; previous < boundedOrdinal; previous++) {
            if (generatedSizeClassAtOrdinal(profile, previous) == sizeClass) count++;
        }
        if (ordinal >= profile.totalNodes() && sizeClass == AsteroidSizeClass.SMALL) {
            count += ordinal - profile.totalNodes();
        }
        return count;
    }

    private static int generatedSizeClassCount(AsteroidFieldProfile profile, AsteroidSizeClass sizeClass) {
        return switch (sizeClass) {
            case LARGE -> profile.largeCount();
            case MEDIUM -> profile.mediumCount();
            case SMALL -> profile.smallCount();
        };
    }
}
