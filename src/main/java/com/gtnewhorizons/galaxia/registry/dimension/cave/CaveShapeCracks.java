package com.gtnewhorizons.galaxia.registry.dimension.cave;

import java.util.Random;

import net.minecraft.world.gen.NoiseGeneratorOctaves;

public class CaveShapeCracks implements CaveShape {

    private static final int CHUNK_WIDTH = 16;
    private static final int HEIGHT_LIMIT = 256;
    private static final double HORIZONTAL_CAVE_STRETCH = 0.1;
    private static final double VERTICAL_CAVE_STRETCH = 0.1;
    private static final int CHUNK_AREA = 256;

    private final double[][] caveCache = new double[CHUNK_AREA][HEIGHT_LIMIT];

    private static NoiseGeneratorOctaves caveNoise;

    private boolean preparedCaveCache = false;
    private int cacheX;
    private int cacheZ;

    @Override
    public void prepareCaveShape(Random random) {
        caveNoise = new NoiseGeneratorOctaves(random, 4);
    }

    @Override
    public boolean preparedCaveShape() {
        return caveNoise != null;
    }

    @Override
    public void prepareCaveCache(int chunkX, int chunkZ) {
        double[] horizontalLayer = caveNoise.generateNoiseOctaves(
            new double[CHUNK_AREA],
            chunkZ * CHUNK_WIDTH,
            chunkX * CHUNK_WIDTH,
            CHUNK_WIDTH,
            CHUNK_WIDTH,
            HORIZONTAL_CAVE_STRETCH,
            HORIZONTAL_CAVE_STRETCH,
            0);
        for (int i = 0; i < horizontalLayer.length; i++) {
            double noise = horizontalLayer[i];
            noise += 8;
            noise /= 16;
            caveCache[i][0] = noise;
        }
        double[] verticalSlice = caveNoise.generateNoiseOctaves(
            new double[HEIGHT_LIMIT],
            chunkZ,
            chunkX,
            HEIGHT_LIMIT,
            1,
            VERTICAL_CAVE_STRETCH,
            VERTICAL_CAVE_STRETCH,
            0);
        for (int i = 0; i < verticalSlice.length; i++) {
            double noise = verticalSlice[i];
            noise += 8;
            noise /= 16;
            verticalSlice[i] = noise;
        }
        for (int i = 0; i < caveCache.length; i++) {
            double baseNoise = caveCache[i][0];
            for (int j = 1; j < verticalSlice.length; j++) {
                caveCache[i][j] = (baseNoise + verticalSlice[j]) / 2;
            }
        }
        preparedCaveCache = true;
        cacheX = chunkX;
        cacheZ = chunkZ;
    }

    @Override
    public boolean preparedCaveCache(int chunkX, int chunkZ) {
        return preparedCaveCache && chunkX == cacheX && chunkZ == cacheZ;
    }

    @Override
    public boolean generateCave(int localX, int localY, int localZ, int height) {
        if (localY >= HEIGHT_LIMIT) {
            return false;
        }
        double localNoise = caveCache[localX + localZ * CHUNK_WIDTH][localY];
        double boundTightening;
        int ceilingDistance = height - localY;
        if (ceilingDistance > 0 && ceilingDistance < CHUNK_WIDTH) {
            boundTightening = 0.75 / ceilingDistance;
        } else if (localY > 4) {
            boundTightening = 0;
        } else {
            boundTightening = (double) 1 / (Math.max(localY - 1, 1));
        }
        double lowerBound = 0.45;
        double upperBound = 0.5 - 0.05 * boundTightening;
        return localNoise < upperBound && localNoise > lowerBound;
    }
}
