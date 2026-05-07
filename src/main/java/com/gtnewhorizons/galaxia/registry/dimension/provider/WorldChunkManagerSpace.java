package com.gtnewhorizons.galaxia.registry.dimension.provider;

import java.util.Random;

import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

/**
 * A specific implementation of the WorldChunkManager to be used on Galaxia planets
 */
public class WorldChunkManagerSpace extends WorldChunkManager {

    private BiomeGenBase[][] biomeGeneratorMatrix;
    private NoiseGeneratorOctaves xBiomeNoise;
    private NoiseGeneratorOctaves zBiomeNoise;

    private boolean cacheCreated = false;
    private int cacheX = 0;
    private int cacheZ = 0;
    private int cacheBiomeIndexX = 0;
    private int cacheBiomeIndexZ = 0;
    private double cacheNoiseX = 0;
    private double cacheNoiseZ = 0;
    private double xStretch = 0;
    private double zStretch = 0;
    private boolean cachedStretching = false;

    /**
     * Assigns the seed to generate specific noise outputs
     *
     * @param seed The seed with which to generate
     */
    public void assignSeed(long seed) {
        // Ignore if no required noise
        if (xBiomeNoise != null) {
            return;
        }
        xBiomeNoise = new NoiseGeneratorOctaves(new Random(seed), 4);
        zBiomeNoise = new NoiseGeneratorOctaves(new Random(seed + 1), 4);
    }

    /**
     * Provides the matrix of biomes to the manager
     *
     * @param biomes The matrix of biome gen bases to be used
     */
    public void provideBiomes(BiomeGenBase[][] biomes) {
        if (biomeGeneratorMatrix != null) {
            return;
        }
        biomeGeneratorMatrix = biomes;
    }

    /**
     * Returns the BiomeGenBase related to the given x, z coordinates in world
     *
     * @param x The checked x coordinate
     * @param z The checked z coordinate
     * @return The BiomeGenBase at that coordinate point on planet
     */
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        if (!(cacheCreated && x == cacheX && z == cacheZ)) {
            if (biomeGeneratorMatrix.length == 1 && biomeGeneratorMatrix[0].length == 1) {
                cacheX = x;
                cacheZ = z;
                cacheCreated = true;
                cacheBiomeIndexX = 0;
                cacheBiomeIndexZ = 0;
                return biomeGeneratorMatrix[cacheBiomeIndexX][cacheBiomeIndexZ];
            }
            cacheBiomeIndexX = getBiomeIndex(x, z, biomeGeneratorMatrix.length, xBiomeNoise, true);
            cacheBiomeIndexZ = getBiomeIndex(x, z, biomeGeneratorMatrix[0].length, zBiomeNoise, false);
            cacheX = x;
            cacheZ = z;
            cacheCreated = true;
        }
        return biomeGeneratorMatrix[cacheBiomeIndexX][cacheBiomeIndexZ];
    }

    /**
     * Gets the index of the biome in the matrix given indices to check
     *
     * @param x              The x index of the matrix to check
     * @param z              The z index of the matrix to check
     * @param matrixLength   The size of the matrix (i.e. 3 for a 3x3)
     * @param noiseGenerator The noise generator used for biome distribution
     * @param firstIndex     Whether this biome is the first index or not
     * @return The index of the biome in the matrix
     */
    private int getBiomeIndex(int x, int z, int matrixLength, NoiseGeneratorOctaves noiseGenerator,
        boolean firstIndex) {
        if (!cachedStretching) {
            xStretch = 0.075 / biomeGeneratorMatrix.length;
            zStretch = 0.075 / biomeGeneratorMatrix[0].length;
            cachedStretching = true;
        }
        double noise = noiseGenerator.generateNoiseOctaves(null, z, x, 1, 1, xStretch, zStretch, 0)[0];
        // normalize
        noise = (noise + 8) / 16;
        noise *= matrixLength;
        if (firstIndex) {
            cacheNoiseX = noise;
        } else {
            cacheNoiseZ = noise;
        }
        int flooredNoise = (int) Math.floor(noise);
        if (flooredNoise < 0) {
            flooredNoise = 0;
        } else if (flooredNoise >= matrixLength) {
            flooredNoise = matrixLength - 1;
        }
        return flooredNoise;
    }

    /**
     * Gets all contributing biomes for use in smoothing methods
     *
     * @return An array of BiomeGenBases storing neighbouring biomes
     */
    public BiomeGenBase[] getLocalBiomes(int x, int z) {
        BiomeGenBase[] localBiomes = new BiomeGenBase[4];
        getLocalBiomes(x, z, localBiomes);
        return localBiomes;
    }

    public void getLocalBiomes(int x, int z, BiomeGenBase[] out) {
        out[0] = this.getBiomeGenAt(x, z);
        int adjacentIndexX = cacheBiomeIndexX + 1 >= biomeGeneratorMatrix.length ? 0 : cacheBiomeIndexX + 1;
        int adjacentIndexZ = cacheBiomeIndexZ + 1 >= biomeGeneratorMatrix[0].length ? 0 : cacheBiomeIndexZ + 1;
        out[1] = biomeGeneratorMatrix[adjacentIndexX][cacheBiomeIndexZ];
        out[2] = biomeGeneratorMatrix[cacheBiomeIndexX][adjacentIndexZ];
        out[3] = biomeGeneratorMatrix[adjacentIndexX][adjacentIndexZ];
    }

    /**
     * Calculates the significance of the terrain features for adjacent biomes
     *
     * @param divergence Width of the blending area
     * @return Significance values for main biome and three corner biomes
     */
    public double[] getLocalBiomeSignificance(double divergence) {
        double[] out = new double[4];
        getLocalBiomeSignificance(divergence, out);
        return out;
    }

    public void getLocalBiomeSignificance(double divergence, double[] out) {
        if (divergence == 0) {
            out[0] = 1;
            out[1] = 0;
            out[2] = 0;
            out[3] = 0;
            return;
        }
        // The first step of calculating divergence is to calculate the deviation from the main biome
        // This is done to reduce the biome noise to a value between 0 and 1
        double xDeviation = cacheNoiseX - Math.floor(cacheNoiseX);
        double zDeviation = cacheNoiseZ - Math.floor(cacheNoiseZ);
        // The deviation range can then be shortened and capped to be within the divergence range
        double divergenceInverse = 1 - divergence;
        xDeviation = Math.max(0, xDeviation - divergenceInverse);
        zDeviation = Math.max(0, zDeviation - divergenceInverse);
        // The remaining deviation is then multiplied to counteract lowering of the values caused by prior shifting
        double deviationMultiplier = 1 / divergence;
        xDeviation *= deviationMultiplier;
        zDeviation *= deviationMultiplier;
        // Calculate proximity values for final calculation steps
        double xProximity = 1 - xDeviation;
        double zProximity = 1 - zDeviation;
        // Four ways normalized symmetric blending in the corner
        out[0] = xProximity * zProximity;
        out[1] = xDeviation * zProximity;
        out[2] = xProximity * zDeviation;
        out[3] = xDeviation * zDeviation;
        // Multiply all values if the total significance is not 1
        double sum = out[0] + out[1] + out[2] + out[3];
        double correctionFactor = 1;
        if (sum == 0) {
            System.out.println("CRITICAL MATH ERROR: TOTAL BIOME SIGNIFICANCE IS 0");
        } else {
            correctionFactor /= sum;
        }
        out[0] *= correctionFactor;
        out[1] *= correctionFactor;
        out[2] *= correctionFactor;
        out[3] *= correctionFactor;
    }

    public int getBiomeCount() {
        int matrixLength = biomeGeneratorMatrix.length;
        int matrixWidth = biomeGeneratorMatrix[0].length;
        return matrixLength * matrixWidth;
    }
}
