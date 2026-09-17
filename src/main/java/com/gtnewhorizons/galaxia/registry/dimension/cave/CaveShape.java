package com.gtnewhorizons.galaxia.registry.dimension.cave;

import java.util.Random;

/**
 * Interface for cave generation on Galaxia planets. Used both for crust and mantle layers
 */
public interface CaveShape {

    /**
     * Provides important data to set up noise objects
     * 
     * @param random Randomizer for noise
     */
    void prepareCaveShape(Random random);

    /**
     * Checks if the cave shape is ready for use
     * 
     * @return Status of readiness
     */
    boolean preparedCaveShape();

    /**
     * Prepares a cache to prevent duplicate calculations
     * 
     * @param chunkX x coordinate of the current chunk
     * @param chunkZ z coordinate of the current chunk
     */
    void prepareCaveCache(int chunkX, int chunkZ);

    /**
     * Checks if a cache is available for a given chunk
     * 
     * @param chunkX x coordinate of the current chunk
     * @param chunkZ z coordinate of the current chunk
     * @return Status of availability
     */
    boolean preparedCaveCache(int chunkX, int chunkZ);

    /**
     * Checks if a specific block is within a cave
     * 
     * @param localX x coordinate within the chunk
     * @param localY y coordinate within the chunk
     * @param localZ z coordinate within the chunk
     * @param height Maximum height of the caves
     * @return Whether the block is within a cave
     */
    boolean isInCave(int localX, int localY, int localZ, int height);
}
