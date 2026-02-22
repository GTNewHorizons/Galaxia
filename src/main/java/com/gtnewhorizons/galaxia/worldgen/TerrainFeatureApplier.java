package com.gtnewhorizons.galaxia.worldgen;

import java.util.Random;

import net.minecraft.world.gen.NoiseGeneratorOctaves;

public final class TerrainFeatureApplier {

    private static NoiseGeneratorOctaves generationNoise;

    // TODO improve formulas for all features
    public static void applyToHeightmap(TerrainFeature feature, int[] heightMap, int chunkX, int chunkZ, Random rand,
        double[] terrainRelevance) {
        if (generationNoise == null) {
            generationNoise = new NoiseGeneratorOctaves(rand, 4);
        }
        TerrainPreset preset = feature.preset();
        double height = feature.height();
        double width = feature.width();
        double freq = feature.frequency();
        int depth = feature.depth();
        long seed = (chunkX * 341873128712L + chunkZ * 132897987541L) ^ rand.nextLong();
        Random localRand = new Random(seed);

        switch (preset) {
            case SAND_DUNES:
                applySandDunes(heightMap, height, width, localRand, chunkX, chunkZ, terrainRelevance);
                break;
            case IMPACT_CRATERS:
                applyImpactCraters(heightMap, height, depth, localRand);
                break;
            case CENTRAL_PEAK_CRATERS:
                applyCentralPeakCraters(heightMap, height, depth, localRand);
                break;
            case MOUNTAIN_RANGES:
                applyMountainRanges(
                    heightMap,
                    height,
                    width,
                    feature.minHeight(),
                    feature.variation(),
                    localRand,
                    chunkX,
                    chunkZ,
                    terrainRelevance);
                break;
            case CANYONS:
                applyCanyons(heightMap, height, depth, localRand);
                break;
            case LAVA_PLATEAUS:
                applyLavaPlateaus(heightMap, height, localRand);
                break;
            case RIVER_VALLEYS:
                applyRiverValleys(heightMap, height, depth, localRand);
                break;
            case YARDANGS:
                applyYardangs(heightMap, height, localRand);
                break;
            case SALT_FLATS:
                applySaltFlats(heightMap, height, localRand);
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

    private static void applySandDunes(int[] hm, double height, double width, Random r, int chunkX, int chunkZ,
        double[] terrainRelevance) {
        double[] noise = generatePerlinNoise(chunkX, chunkZ, 1 / (width * 4));
        chunkX *= 16;
        chunkZ *= 16;
        for (int x = 15; x >= 0; x--) {
            for (int z = 15; z >= 0; z--) {
                double localRelevance = terrainRelevance[x + z * 16];
                if (localRelevance == 0) {
                    continue;
                }
                double localNoise = (noise[x + z * 16] + 5) / 10;
                double wave = Math.sin(((chunkX + x) * 0.7 + (chunkZ + z) * 0.4) / (width * 4)) * height * localNoise;
                hm[x + z * 16] += (int) (wave * height * localRelevance);
            }
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

    private static void applyMountainRanges(int[] hm, double height, double width, int minH, int var, Random r,
        int chunkX, int chunkZ, double[] terrainRelevance) {
        double[] noise = generatePerlinNoise(chunkX, chunkZ, 1 / (width * 4));
        for (int x = 15; x >= 0; x--) {
            for (int z = 15; z >= 0; z--) {
                double localRelevance = terrainRelevance[x + z * 16];
                if (localRelevance == 0) {
                    continue;
                }
                hm[x + z * 16] += (int) ((minH + noise[x + z * 16] * height) * localRelevance);
            }
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

    private static double[] generatePerlinNoise(int chunkX, int chunkZ, double scale) {
        chunkX *= 16;
        chunkZ *= 16;
        return generationNoise.generateNoiseOctaves(new double[256], chunkZ, chunkX, 16, 16, scale, scale, 0);
    }
}
