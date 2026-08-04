package com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.ChunkProviderGalaxiaPlanet;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.math.LinearFunction3D;

import java.util.Random;

/**
 * Terrain noise which calculates many tubes within a large region
 */
public class TubeNoise {

    private static final byte CHUNK_BITSHIFT = 4;
    private static final byte ADDITIONAL_BITSHIFT = 4;
    private static final byte TOTAL_BITSHIFT = CHUNK_BITSHIFT + ADDITIONAL_BITSHIFT;
    private static final short COORDINATE_BOUND = 2 << TOTAL_BITSHIFT;
    private static final short SHIFT_MARGIN = 2 << (TOTAL_BITSHIFT - 1);
    private static final short TUBE_COUNT = 128;
    private static final byte BASE_TUBE_HEIGHT = 16;
    private static final int TUBE_HEIGHT_VARIATION = ChunkProviderGalaxiaPlanet.HEIGHT_LIMIT >> 4;

    private final Random xRandom = new Random();
    private final Random zRandom = new Random();
    private final LinearFunction3D[] linearFunctions = new LinearFunction3D[TUBE_COUNT];
    private final int[] xEndPoints = new int[TUBE_COUNT];
    private final int[] xStartPoints = new int[TUBE_COUNT];
    private final short[] deviationMargins = new short[TUBE_COUNT];
    private final float verticalInclinationMultiplier;

    private boolean cached = false;
    private long seed;
    private int cacheChunkX;
    private int cacheChunkZ;
    private int quadrantX;
    private int quadrantZ;

    /**
     * Creates the tube noise and sets up the linear functions
     * @param verticalInclinationMultiplier Inclination multiplier to determine tube steepness
     */
    public TubeNoise(float verticalInclinationMultiplier) {
        for (int i = 0; i < linearFunctions.length; i++) {
            linearFunctions[i] = new LinearFunction3D();
        }
        this.verticalInclinationMultiplier = verticalInclinationMultiplier;
    }

    public boolean isCached() {
        return cached;
    }

    public void setSeed(Random random) {
        seed = random.nextLong();
    }

    /**
     * Checks if a specific block is intersecting with any of the tubes
     * @param x Global x coordinate of the block
     * @param y Global y coordinate of the block
     * @param z Global z coordinate of the block
     * @param diameterModifier Diameter modifier of the tubes at the given coordinates
     * @return Whether the block is intersecting any of the tubes
     */
    public boolean isIntersectingTube(int x, int y, int z, double diameterModifier) {
        x = Math.abs(x);
        z = Math.abs(z);
        x += quadrantX << ADDITIONAL_BITSHIFT;
        z += quadrantZ << ADDITIONAL_BITSHIFT;
        for (int i = 0; i < TUBE_COUNT; i++) {
            if (x > xEndPoints[i]) continue;
            if (x < xStartPoints[i]) continue;
            float deviation = linearFunctions[i].getDeviation(x, y, z);
            if (deviation * deviation < deviationMargins[i] * diameterModifier) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the current tube cache is outside the currently generated chunk
     * @param chunkX x coordinate of the current chunk
     * @param chunkZ z coordinate of the current chunk
     * @return Whether the cache is in a different chunk
     */
    public boolean isInDifferentChunk(int chunkX, int chunkZ) {
        return chunkX != cacheChunkX || chunkZ != cacheChunkZ;
    }

    /**
     *
     * @param chunkX
     * @param chunkZ
     * @param baseTubeDiameter
     * @param varyingTubeDiameter
     * @param tubeLength
     */
    public void updateCache(int chunkX, int chunkZ, byte baseTubeDiameter, byte varyingTubeDiameter, short tubeLength) {
        int xQuadrant = chunkX >> ADDITIONAL_BITSHIFT;
        int zQuadrant = chunkZ >> ADDITIONAL_BITSHIFT;
        quadrantX = chunkX - (xQuadrant << ADDITIONAL_BITSHIFT);
        quadrantZ = chunkZ - (zQuadrant << ADDITIONAL_BITSHIFT);
        cacheChunkX = chunkX;
        cacheChunkZ = chunkZ;
        cached = true;
        xRandom.setSeed(seed + xQuadrant);
        zRandom.setSeed(seed + zQuadrant);
        for (int i = 0; i < TUBE_COUNT; i++) {
            float zInclination = xRandom.nextFloat();
            if (xRandom.nextBoolean()) {
                zInclination = -zInclination;
            }
            float xyInclination = xRandom.nextFloat() * verticalInclinationMultiplier;
            if (xRandom.nextBoolean()) {
                xyInclination = -xyInclination;
            }
            float zyInclination = zRandom.nextFloat() * verticalInclinationMultiplier;
            if (zRandom.nextBoolean()) {
                zyInclination = -zyInclination;
            }
            linearFunctions[i].setFunction(
                zRandom.nextInt(COORDINATE_BOUND) - SHIFT_MARGIN,
                xRandom.nextInt(TUBE_HEIGHT_VARIATION) + BASE_TUBE_HEIGHT,
                zRandom.nextInt(TUBE_HEIGHT_VARIATION) + BASE_TUBE_HEIGHT,
                zInclination,
                xyInclination,
                zyInclination);
            xEndPoints[i] = xRandom.nextInt(COORDINATE_BOUND) + 1;
            xStartPoints[i] = xRandom.nextInt(Math.max(1, xEndPoints[i] - tubeLength));
            deviationMargins[i] = (short) (baseTubeDiameter
                + xRandom.nextInt(baseTubeDiameter) * zRandom.nextInt(varyingTubeDiameter + 1));
        }
    }
}
