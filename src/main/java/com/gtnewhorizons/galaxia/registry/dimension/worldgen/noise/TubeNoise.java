package com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.math.LinearFunction2D;

import java.util.Random;

public class TubeNoise {
    private static final byte CHUNK_BITSHIFT = 4;
    private static final byte ADDITIONAL_BITSHIFT = 4;
    private static final byte TOTAL_BITSHIFT = CHUNK_BITSHIFT + ADDITIONAL_BITSHIFT;
    private static final short COORDINATE_BOUND = 2 << TOTAL_BITSHIFT;
    private static final byte MINIMUM_TUBE_LENGTH = 100;
    private static final byte DEVIATION_MARGIN = 4;
    private static final short Z_SHIFT_MARGIN = 2 << (TOTAL_BITSHIFT - 1);
    private static final byte INCLINATION_MULTIPLIER = 8;

    private final Random xRandom = new Random();
    private final Random zRandom = new Random();
    private final LinearFunction2D linearFunction = new LinearFunction2D();

    private boolean cached = false;
    private long seed;
    private int xStartPoint;
    private int xEndPoint;
    private int cacheChunkX;
    private int cacheChunkZ;
    private int quadrantX;
    private int quadrantZ;

    public boolean isCached() {
        return cached;
    }

    public void setSeed(Random random) {
        seed = random.nextLong();
    }

    public boolean isIntersectingTube(int x, int z) {
        x = Math.abs(x);
        z = Math.abs(z);
        x += quadrantX << ADDITIONAL_BITSHIFT;
        z += quadrantZ << ADDITIONAL_BITSHIFT;
        if (x > xEndPoint) return false;
        if (x < xStartPoint) return false;
        float deviation = Math.abs(z - linearFunction.getLocalY(x));
        return deviation < DEVIATION_MARGIN;
    }

    public boolean isInDifferentChunk(int chunkX, int chunkZ) {
        return chunkX != cacheChunkX || chunkZ != cacheChunkZ;
    }

    public void updateCache(int chunkX, int chunkZ) {
        int xQuadrant = chunkX >> ADDITIONAL_BITSHIFT;
        int zQuadrant = chunkZ >> ADDITIONAL_BITSHIFT;
        quadrantX = chunkX - (xQuadrant << ADDITIONAL_BITSHIFT);
        quadrantZ = chunkZ - (zQuadrant << ADDITIONAL_BITSHIFT);
        cacheChunkX = chunkX;
        cacheChunkZ = chunkZ;
        cached = true;
        xRandom.setSeed(seed + xQuadrant);
        zRandom.setSeed(seed + zQuadrant);
        xEndPoint = xRandom.nextInt(COORDINATE_BOUND) + 1;
        xStartPoint = xRandom.nextInt(Math.max(1, xEndPoint - MINIMUM_TUBE_LENGTH));
        float inclination = xRandom.nextFloat() * INCLINATION_MULTIPLIER;
        if (xRandom.nextBoolean()) {
            inclination = -inclination;
        }
        linearFunction.setFunction(zRandom.nextInt(COORDINATE_BOUND) - Z_SHIFT_MARGIN, inclination);
    }
}
