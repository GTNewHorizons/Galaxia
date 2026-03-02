package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.Random;

import net.minecraft.world.gen.NoiseGeneratorOctaves;

/**
 * Class to deal with actual application of different feature types
 */
public final class TerrainFeatureApplier {

    private static NoiseGeneratorOctaves generationNoise;

    // TODO improve formulas for all features

    /**
     * Applies features to height map provided in given chunks
     *
     * @param feature          The feature to apply
     * @param heightMap        The current height map
     * @param chunkX           The chunk x coordinate
     * @param chunkZ           The chunk z coordinate
     * @param rand             Random instance
     * @param terrainRelevance Matrix holding the terrain precedence
     */
    public static void applyToHeightmap(TerrainFeature feature, double[] heightMap, int chunkX, int chunkZ, Random rand,
        double[] terrainRelevance) {
        if (generationNoise == null) {
            generationNoise = new NoiseGeneratorOctaves(rand, 4);
        }
        TerrainPreset preset = feature.preset();
        double height = feature.height();
        double width = feature.width();
        long seed = (chunkX * 341873128712L + chunkZ * 132897987541L) ^ rand.nextLong();
        Random localRand = new Random(seed);

        switch (preset) {
            case SAND_DUNES:
                applySandDunes(heightMap, height, width, chunkX, chunkZ, terrainRelevance);
                break;
            case IMPACT_CRATERS:
                applyImpactCraters(heightMap, width, height, localRand);
                break;
            case CENTRAL_PEAK_CRATERS:
                applyCentralPeakCraters(heightMap, width, height, localRand);
                break;
            case MOUNTAIN_RANGES:
                applyMountainRanges(heightMap, height, width, chunkX, chunkZ, terrainRelevance);
                break;
            case CANYONS:
                applyCanyons(heightMap, width, height, chunkX, chunkZ, terrainRelevance);
                break;
            case LAVA_PLATEAUS:
                applyLavaPlateaus(heightMap, height, localRand);
                break;
            case RIVER_VALLEYS:
                applyRiverValleys(heightMap, width, height, localRand);
                break;
            case YARDANGS:
                applyYardangs(heightMap, height, localRand);
                break;
            case SALT_FLATS:
                applySaltFlats(heightMap, height, localRand);
                break;
            case BASE_HEIGHT:
                applyBaseHeight(heightMap, height, terrainRelevance);
                break;
            case SHIELD_VOLCANOES:
                applyShieldVolcanoes(heightMap, height, width, chunkX, chunkZ, terrainRelevance);
                break;
            case MULTI_RING_BASINS:
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

    /**
     * Applies sand dunes to a height map
     *
     * @param hm               The current height map
     * @param height           The height of the sand dunes
     * @param width            The width of the sand dunes
     * @param chunkX           The chunk x coordinate
     * @param chunkZ           The chunk z coordinate
     * @param terrainRelevance Matrix holding the terrain precedence
     */
    private static void applySandDunes(double[] hm, double height, double width, int chunkX, int chunkZ,
        double[] terrainRelevance) {
        double[] noise = generatePerlinNoise(chunkX, chunkZ, 1 / (width * 4));
        chunkX *= 16;
        chunkZ *= 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double localRelevance = terrainRelevance[x + z * 16];
                if (localRelevance == 0) {
                    continue;
                }
                double localNoise = (noise[x + z * 16] + 5) / 10;
                double wave = Math.sin(((chunkX + x) * 0.7 + (chunkZ + z) * 0.4) / (width * 4)) * localNoise;
                hm[x + z * 16] += wave * height * localRelevance;
            }
        }
    }

    /**
     * Applies impact craters as a terrain feature
     *
     * @param hm     Current height map
     * @param width  Size of the craters (radius)
     * @param height Depth of the craters
     * @param r      Random instance
     */
    private static void applyImpactCraters(double[] hm, double width, double height, Random r) {
        int cx = 8 + r.nextInt(4) - 2;
        int cz = 8 + r.nextInt(4) - 2;
        for (int i = 0; i < 256; i++) {
            int x = i & 15, z = i >> 4;
            double dist = Math.hypot(x - cx, z - cz);
            if (dist < 7 * width) {
                double falloff = 1 - dist / (7 * width);
                hm[i] -= height * falloff * falloff;
            }
        }
    }

    /**
     * Applies Central Peak Craters to the height map
     *
     * @param hm     Current height map
     * @param width  Size of the craters (radius)
     * @param height Depth of the craters
     * @param r      Random instance
     */
    private static void applyCentralPeakCraters(double[] hm, double width, double height, Random r) {
        applyImpactCraters(hm, width, height, r);
        int px = 7 + r.nextInt(2);
        int pz = 7 + r.nextInt(2);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int i = (px + dx) + (pz + dz) * 16;
                if (i >= 0 && i < 256) hm[i] += (18 * width);
            }
        }
    }

    /**
     * Applies mountain ranges to the height map
     *
     * @param hm               The height map
     * @param height           Target mountain range height
     * @param width            Target mountain range width
     * @param chunkX           Chunk x coordinates
     * @param chunkZ           Chunk z coordinates
     * @param terrainRelevance Matrix holding the terrain precedence
     */
    private static void applyMountainRanges(double[] hm, double height, double width, int chunkX, int chunkZ,
        double[] terrainRelevance) {
        double[] noise = generatePerlinNoise(chunkX, chunkZ, 1 / (width * 4));
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double localRelevance = terrainRelevance[x + z * 16];
                if (localRelevance == 0) {
                    continue;
                }
                hm[x + z * 16] += ((noise[x + z * 16] * height) * localRelevance);
            }
        }
    }

    /**
     * Applies canyons to the height map
     *
     * @param hm     The height map
     * @param width  The canyon size
     * @param height The depth of the canyon
     */
    private static void applyCanyons(double[] hm, double width, double height, int chunkX, int chunkZ,
        double[] terrainRelevance) {
        double[] noise = generatePerlinNoise(chunkX, chunkZ, 1 / (width * 4));
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double localRelevance = terrainRelevance[x + z * 16];
                if (localRelevance == 0) {
                    continue;
                }
                double localNoise = noise[x + z * 16];
                localNoise = Math.abs(localNoise - 0.5);
                localNoise *= 10;
                if (localNoise < 3 && localNoise > 2) {
                    localNoise = 0.5 - Math.abs(localNoise - 2.5);
                    hm[x + z * 16] -= (((localNoise) * 2 * height) * localRelevance);
                }
            }
        }
    }

    /**
     * Applies lava plateaus to the height map
     *
     * @param hm   The height map
     * @param size The plateau size
     * @param r    Random instance
     */
    private static void applyLavaPlateaus(double[] hm, double size, Random r) {
        for (int i = 0; i < 256; i++) hm[i] += (12 * size);
    }

    /**
     * Applies river valleys to the height map
     *
     * @param hm     The height map
     * @param width  The river valley size
     * @param height The depth of the river valley
     * @param r      Random instance
     */
    private static void applyRiverValleys(double[] hm, double width, double height, Random r) {
        for (int i = 0; i < 256; i++) {
            if ((i & 15) > 5 && (i & 15) < 11) hm[i] -= (height * width * 0.7);
        }
    }

    /**
     * Applies yardangs to the heightmap
     *
     * @param hm   The height map
     * @param size The size of the yardangs
     * @param r    Random instance
     */
    private static void applyYardangs(double[] hm, double size, Random r) {
        for (int i = 0; i < 256; i++) hm[i] += (Math.sin((i & 15) * 1.8) * 4 * size);
    }

    /**
     * Applies salt flats to the heightmap
     *
     * @param hm   The height map
     * @param size The size of the salt flats
     * @param r    Random instance
     */
    private static void applySaltFlats(double[] hm, double size, Random r) {
        for (int i = 0; i < 256; i++) hm[i] = Math.max(2, hm[i] - 3);
    }

    private static void applyBaseHeight(double[] hm, double height, double[] terrainRelevance) {
        for (int i = 0; i < 256; i++) {
            hm[i] += (height * terrainRelevance[i]);
        }
    }

    /**
     * Applies mountain ranges to the height map
     *
     * @param hm               The height map
     * @param height           Target mountain range height
     * @param width            Target mountain range width
     * @param chunkX           Chunk x coordinates
     * @param chunkZ           Chunk z coordinates
     * @param terrainRelevance Matrix holding the terrain precedence
     */
    private static void applyShieldVolcanoes(double[] hm, double height, double width, int chunkX, int chunkZ,
        double[] terrainRelevance) {
        double[] noise = generatePerlinNoise(chunkX, chunkZ, 1 / (width * 4));
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double localRelevance = terrainRelevance[x + z * 16];
                if (localRelevance == 0) {
                    continue;
                }
                double localNoise = noise[x + z * 16];
                if (localNoise > 0.75) {
                    continue;
                }
                hm[x + z * 16] += ((localNoise * height) * localRelevance);
            }
        }
    }

    /**
     * Applies generic noise to the height map
     *
     * @param hm     The current height map
     * @param preset The terrain preset to use
     * @param size   The size of the noise application
     * @param r      Random instance
     */
    private static void applyGenericNoise(double[] hm, TerrainPreset preset, double size, Random r) {
        for (int i = 0; i < 256; i++) {
            hm[i] += r.nextGaussian() * 6 * size;
        }
    }

    /**
     * Generates Perlin noise for a given chunk
     *
     * @param chunkX Chunk x coordinates
     * @param chunkZ Chunk y coordinates
     * @param scale  the scale of the perlin noise effect
     * @return A matrix of variations based on noise output
     */
    private static double[] generatePerlinNoise(int chunkX, int chunkZ, double scale) {
        chunkX *= 16;
        chunkZ *= 16;
        double[] noise = generationNoise.generateNoiseOctaves(new double[256], chunkZ, chunkX, 16, 16, scale, scale, 0);
        for (int i = 0; i < noise.length; i++) {
            double localNoise = noise[i];
            localNoise += 8;
            localNoise /= 16;
            if (localNoise < 0) {
                localNoise = 0;
            } else if (localNoise > 1) {
                localNoise = 1;
            }
            noise[i] = localNoise;
        }
        return noise;
    }
}
