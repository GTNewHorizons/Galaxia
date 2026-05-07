package com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.ChunkProviderGalaxiaPlanet;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.math.LinearFunction3D;

import java.util.Random;

public class TubeNoise {
    private static final byte CHUNK_BITSHIFT = 4;
    private static final byte ADDITIONAL_BITSHIFT = 4;
    private static final byte TOTAL_BITSHIFT = CHUNK_BITSHIFT + ADDITIONAL_BITSHIFT;
    private static final short COORDINATE_BOUND = 2 << TOTAL_BITSHIFT;
    private static final byte MINIMUM_TUBE_LENGTH = 100;
    private static final short DEVIATION_MARGIN = 16;
    private static final short SHIFT_MARGIN = 2 << (TOTAL_BITSHIFT - 1);
    private static final byte HORIZONTAL_INCLINATION_MULTIPLIER = 16;
    private static final float VERTICAL_INCLINATION_MULTIPLIER = 0.5F;

    private final Random xRandom = new Random();
    private final Random zRandom = new Random();
    private final LinearFunction3D linearFunction = new LinearFunction3D();

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

    public boolean isIntersectingTube(int x, int y, int z) {
        x = Math.abs(x);
        z = Math.abs(z);
        x += quadrantX << ADDITIONAL_BITSHIFT;
        z += quadrantZ << ADDITIONAL_BITSHIFT;
        if (x > xEndPoint) return false;
        if (x < xStartPoint) return false;
        return linearFunction.getDeviation(x, y, z) < DEVIATION_MARGIN;
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
        float zInclination = xRandom.nextFloat() * HORIZONTAL_INCLINATION_MULTIPLIER;
        if (xRandom.nextBoolean()) {
            zInclination = -zInclination;
        }
        float xyInclination = xRandom.nextFloat() * VERTICAL_INCLINATION_MULTIPLIER;
        if (xRandom.nextBoolean()) {
            xyInclination = -xyInclination;
        }
        float zyInclination = zRandom.nextFloat() * VERTICAL_INCLINATION_MULTIPLIER;
        if (zRandom.nextBoolean()) {
            zyInclination = -zyInclination;
        }
        linearFunction.setFunction(
            zRandom.nextInt(COORDINATE_BOUND) - SHIFT_MARGIN,
            xRandom.nextInt(ChunkProviderGalaxiaPlanet.HEIGHT_LIMIT >> 3),
            zRandom.nextInt(ChunkProviderGalaxiaPlanet.HEIGHT_LIMIT >> 3),
            zInclination,
            xyInclination,
            zyInclination
        );
    }
}
