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

}
