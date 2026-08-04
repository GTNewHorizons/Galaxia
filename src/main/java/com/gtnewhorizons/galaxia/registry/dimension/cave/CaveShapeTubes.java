package com.gtnewhorizons.galaxia.registry.dimension.cave;

import java.util.Random;

import net.minecraft.world.gen.NoiseGeneratorOctaves;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.TubeNoise;

/**
 * Cave generator for long tubes
 */
public class CaveShapeTubes implements CaveShape {

    private static final int CHUNK_AREA = 256;
    private static final int CHUNK_WIDTH = 16;
    private static final double HORIZONTAL_CAVE_STRETCH = 0.1;
    private static final float DEFAULT_VERTICAL_INCLINATION_MULTIPLIER = 0.5F;

    private static TubeNoise tubeNoise;
    private static NoiseGeneratorOctaves sizeNoise;
    private static NoiseGeneratorOctaves horizontalDistortion;
    private static NoiseGeneratorOctaves verticalDistortion;

    private final double[] sizeModifiers = new double[CHUNK_AREA];
    private final int[] horizontalModifiers = new int[CHUNK_AREA];
    private final int[] verticalModifiers = new int[CHUNK_AREA];
    private final byte baseTubeDiameter;
    private final byte varyingTubeDiameter;
    private final short tubeLength;
    private final float verticalInclinationMultiplier;

    /**
     * Simple constructor with a default vertical inclination of 0.5
     *
     * @param baseTubeDiameter    Minimum diameter for all tubes
     * @param varyingTubeDiameter Varying additional size for each tube
     * @param tubeLength          Maximum tube length
     */
    public CaveShapeTubes(byte baseTubeDiameter, byte varyingTubeDiameter, short tubeLength) {
        this(baseTubeDiameter, varyingTubeDiameter, tubeLength, DEFAULT_VERTICAL_INCLINATION_MULTIPLIER);
    }

    public CaveShapeTubes(byte baseTubeDiameter, byte varyingTubeDiameter, short tubeLength,
        float verticalInclinationMultiplier) {
        this.baseTubeDiameter = baseTubeDiameter;
        this.varyingTubeDiameter = varyingTubeDiameter;
        this.tubeLength = tubeLength;
        this.verticalInclinationMultiplier = verticalInclinationMultiplier;
    }

    @Override
    public void prepareCaveShape(Random random) {
        sizeNoise = new NoiseGeneratorOctaves(random, 4);
        horizontalDistortion = new NoiseGeneratorOctaves(random, 4);
        verticalDistortion = new NoiseGeneratorOctaves(random, 4);
        tubeNoise = new TubeNoise(verticalInclinationMultiplier);
        tubeNoise.setSeed(random);
    }

    @Override
    public boolean preparedCaveShape() {
        return tubeNoise != null && sizeNoise != null;
    }

    @Override
    public void prepareCaveCache(int chunkX, int chunkZ) {
        tubeNoise.updateCache(chunkX, chunkZ, baseTubeDiameter, varyingTubeDiameter, tubeLength);
        double[] rawModifiers = sizeNoise.generateNoiseOctaves(
            new double[CHUNK_AREA],
            chunkZ * CHUNK_WIDTH,
            chunkX * CHUNK_WIDTH,
            CHUNK_WIDTH,
            CHUNK_WIDTH,
            HORIZONTAL_CAVE_STRETCH,
            HORIZONTAL_CAVE_STRETCH,
            0);
        for (int i = 0; i < rawModifiers.length; i++) {
            double noise = rawModifiers[i];
            noise += 8;
            noise /= 8;
            noise += 0.25;
            sizeModifiers[i] = noise;
        }
        double[] rawHorizontalModifiers = horizontalDistortion.generateNoiseOctaves(
            new double[CHUNK_AREA],
            chunkZ * CHUNK_WIDTH,
            chunkX * CHUNK_WIDTH,
            CHUNK_WIDTH,
            CHUNK_WIDTH,
            HORIZONTAL_CAVE_STRETCH,
            HORIZONTAL_CAVE_STRETCH,
            0);
        for (int i = 0; i < rawHorizontalModifiers.length; i++) {
            horizontalModifiers[i] = (int) rawHorizontalModifiers[i];
        }
        double[] rawVerticalModifiers = verticalDistortion.generateNoiseOctaves(
            new double[CHUNK_AREA],
            chunkZ * CHUNK_WIDTH,
            chunkX * CHUNK_WIDTH,
            CHUNK_WIDTH,
            CHUNK_WIDTH,
            HORIZONTAL_CAVE_STRETCH,
            HORIZONTAL_CAVE_STRETCH,
            0);
        for (int i = 0; i < rawVerticalModifiers.length; i++) {
            verticalModifiers[i] = (int) rawVerticalModifiers[i];
        }
    }

    @Override
    public boolean preparedCaveCache(int chunkX, int chunkZ) {
        return tubeNoise.isCached() && !tubeNoise.isInDifferentChunk(chunkX, chunkZ);
    }

    @Override
    public boolean isInCave(int localX, int localY, int localZ, int height) {
        return tubeNoise.isIntersectingTube(
            localX + horizontalModifiers[localX],
            localY + verticalModifiers[localX + (localZ << 4)],
            localZ + horizontalModifiers[localZ << 4],
            sizeModifiers[localX + (localZ << 4)]);
    }
}
