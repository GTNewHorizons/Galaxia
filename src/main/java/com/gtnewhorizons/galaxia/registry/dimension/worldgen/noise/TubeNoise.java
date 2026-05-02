package com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise;

import java.util.Random;

public class TubeNoise {
    private final Random xRandom = new Random();
    private final Random zRandom = new Random();

    private long seed;
    private int xQuadrant;
    private int zQuadrant;
    private int xStartPoint;
    private int zStartPoint;
    private int xEndPoint;
    private int zEndPoint;
    private int cacheChunkX;
    private int cacheChunkZ;

    public void setSeed(Random random) {
        seed = random.nextLong();
    }

    public boolean isIntersectingTube(int x, int z) {
        int excessiveX = x >> 8;
        excessiveX = excessiveX << 8;
        int excessiveZ = z >> 8;
        excessiveZ = excessiveZ << 8;
        x -= excessiveX;
        z -= excessiveZ;
        if (x > xEndPoint) return false;
        if (x < xStartPoint) return false;
        if (z > zEndPoint) return false;
        if (z < zStartPoint) return false;
        float functionInclination = (float) (zEndPoint - zStartPoint) / (xEndPoint - xStartPoint);
        float coordinateInclination = (float) (zEndPoint - z) / (xEndPoint - x);
        float deviation = Math.abs(functionInclination - coordinateInclination);
        return deviation < 2;
    }

    public boolean isInDifferentChunk(int chunkX, int chunkZ) {
        return chunkX != cacheChunkX || chunkZ != cacheChunkZ;
    }

    public void updateQuadrants(int chunkX, int chunkZ) {
        xQuadrant = chunkX >> 4;
        zQuadrant = chunkZ >> 4;
        cacheChunkX = chunkX;
        cacheChunkZ = chunkZ;
    }

    private void calculatePoints() {
        xRandom.setSeed(seed + xQuadrant);
        zRandom.setSeed(seed + zQuadrant);
        xEndPoint = xRandom.nextInt(64) + 1;
        zEndPoint = zRandom.nextInt(64);
        xStartPoint = xRandom.nextInt(xEndPoint);
        zStartPoint = zRandom.nextInt(64);
    }
}
