package com.gtnewhorizons.galaxia.registry.dimension.cave;

import java.util.Random;

import net.minecraft.world.gen.NoiseGeneratorOctaves;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.TubeNoise;

public class CaveShapeTubes implements CaveShape {

    private static final int CHUNK_AREA = 256;
    private static final int CHUNK_WIDTH = 16;
    private static final double HORIZONTAL_CAVE_STRETCH = 0.1;

    private static TubeNoise caveNoise;
    private static NoiseGeneratorOctaves sizeNoise;
    private static NoiseGeneratorOctaves horizontalDistortion;
    private static NoiseGeneratorOctaves verticalDistortion;

    private final double[] sizeModifiers = new double[CHUNK_AREA];
    private final int[] horizontalModifiers = new int[CHUNK_AREA];
    private final int[] verticalModifiers = new int[CHUNK_AREA];
    private final byte baseTubeDiameter;
    private final byte varyingTubeDiameter;
    private final short tubeLength;

    public CaveShapeTubes(byte baseTubeDiameter, byte varyingTubeDiameter, short tubeLength) {
        this.baseTubeDiameter = baseTubeDiameter;
        this.varyingTubeDiameter = varyingTubeDiameter;
        this.tubeLength = tubeLength;
    }

    @Override
    public void prepareCaveShape(Random random) {
        sizeNoise = new NoiseGeneratorOctaves(random, 4);
        horizontalDistortion = new NoiseGeneratorOctaves(random, 4);
        verticalDistortion = new NoiseGeneratorOctaves(random, 4);
        caveNoise = new TubeNoise();
        caveNoise.setSeed(random);
    }

    @Override
    public boolean preparedCaveShape() {
        return caveNoise != null && sizeNoise != null;
    }

    @Override
    public void prepareCaveCache(int chunkX, int chunkZ) {
        caveNoise.updateCache(chunkX, chunkZ, baseTubeDiameter, varyingTubeDiameter, tubeLength);
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
        return caveNoise.isCached() && !caveNoise.isInDifferentChunk(chunkX, chunkZ);
    }

    @Override
    public boolean generateCave(int localX, int localY, int localZ, int height) {
        return caveNoise.isIntersectingTube(
            localX + horizontalModifiers[localX],
            localY + verticalModifiers[localX + (localZ << 4)],
            localZ + horizontalModifiers[localZ << 4],
            sizeModifiers[localX + (localZ << 4)]);
    }
}
