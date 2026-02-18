package com.gtnewhorizons.galaxia.worldgen;

import java.util.Random;

public final class TerrainFeatureApplier {

    // TODO improve formulas for all features
    public static void applyToHeightmap(TerrainFeature feature, int[] heightMap, int chunkX, int chunkZ, Random rand) {
        TerrainPreset preset = feature.getPreset();
        double size = feature.getSize();
        double freq = feature.getFrequency();
        int depth = feature.getDepth();
        long seed = (chunkX * 341873128712L + chunkZ * 132897987541L) ^ rand.nextLong();
        Random localRand = new Random(seed);

        switch (preset) {
            case SAND_DUNES:
                applySandDunes(heightMap, size, localRand);
                break;
            case IMPACT_CRATERS:
                applyImpactCraters(heightMap, size, depth, localRand);
                break;
            case CENTRAL_PEAK_CRATERS:
                applyCentralPeakCraters(heightMap, size, depth, localRand);
                break;
            case MOUNTAIN_RANGES:
                applyMountainRanges(heightMap, size, feature.getMinHeight(), feature.getVariation(), localRand);
                break;
            case CANYONS:
                applyCanyons(heightMap, size, depth, localRand);
                break;
            case LAVA_PLATEAUS:
                applyLavaPlateaus(heightMap, size, localRand);
                break;
            case RIVER_VALLEYS:
                applyRiverValleys(heightMap, size, depth, localRand);
                break;
            case YARDANGS:
                applyYardangs(heightMap, size, localRand);
                break;
            case SALT_FLATS:
                applySaltFlats(heightMap, size, localRand);
                break;
            case MULTI_RING_BASINS:
            case SHIELD_VOLCANOES:
            case PLATEAUS_AND_ESCARPMENTS:
            case TECTONIC_RIFTS:
            case GLACIAL_VALLEYS:
            case LAVA_TUBES:
            case CRYOVOLCANOES:
            case ICE_FISSURES:
            case KARST_SINKHOLES:
            case LAYERED_SEDIMENTARY_ROCKS:
                break;
        }
    }

    private static void applySandDunes(int[] hm, double size, Random r) {
        for (int i = 0; i < 256; i++) {
            int x = i & 15, z = i >> 4;
            double wave = Math.sin(x * 0.7 + z * 0.4) * 7 * size;
            hm[i] += (int) (wave + r.nextGaussian() * 2 * size);
        }
    }

    private static void applyImpactCraters(int[] hm, double size, int depth, Random r) {
        int cx = 8 + r.nextInt(4) - 2;
        int cz = 8 + r.nextInt(4) - 2;
        for (int i = 0; i < 256; i++) {
            int x = i & 15, z = i >> 4;
            double dist = Math.hypot(x - cx, z - cz);
            if (dist < 7 * size) {
                double falloff = 1 - dist / (7 * size);
                hm[i] -= (int) (depth * falloff * falloff);
            }
        }
    }

    private static void applyCentralPeakCraters(int[] hm, double size, int depth, Random r) {
        applyImpactCraters(hm, size, depth, r);
        int px = 7 + r.nextInt(2);
        int pz = 7 + r.nextInt(2);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int i = (px + dx) + (pz + dz) * 16;
                if (i >= 0 && i < 256) hm[i] += (int) (18 * size);
            }
        }
    }

    private static void applyMountainRanges(int[] hm, double size, int minH, int var, Random r) {
        for (int i = 0; i < 256; i++) {
            hm[i] = minH + (int) (var * size * (0.6 + 0.4 * Math.sin(i * 0.13)));
        }
    }

    private static void applyCanyons(int[] hm, double size, int depth, Random r) {
        for (int i = 0; i < 256; i++) {
            if ((i & 15) % 5 == 0) hm[i] -= (int) (depth * size);
        }
    }

    private static void applyLavaPlateaus(int[] hm, double size, Random r) {
        for (int i = 0; i < 256; i++) hm[i] += (int) (12 * size);
    }

    private static void applyRiverValleys(int[] hm, double size, int depth, Random r) {
        for (int i = 0; i < 256; i++) {
            if ((i & 15) > 5 && (i & 15) < 11) hm[i] -= (int) (depth * size * 0.7);
        }
    }

    private static void applyYardangs(int[] hm, double size, Random r) {
        for (int i = 0; i < 256; i++) hm[i] += (int) (Math.sin((i & 15) * 1.8) * 4 * size);
    }

    private static void applySaltFlats(int[] hm, double size, Random r) {
        for (int i = 0; i < 256; i++) hm[i] = Math.max(2, hm[i] - 3);
    }

    private static void applyGenericNoise(int[] hm, TerrainPreset preset, double size, double freq, Random r) {
        for (int i = 0; i < 256; i++) {
            hm[i] += (int) (r.nextGaussian() * 6 * size);
        }
    }
}
