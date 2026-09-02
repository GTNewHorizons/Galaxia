package com.gtnewhorizons.galaxia.registry.dimension.worldgen.details;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.modifier.ModifierHandler;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.modifier.TerrainModifierEntry;

import java.util.Random;

/**
 * 3D terrain generation interface with all the necessary methods
 * <p>
 * 3D terrain is stacked on top of the 2D heightmap
 */
public interface Terrain3D {

    /**
     * Prepares all functions which depend on randomness and a seed
     * @param random Randomizer for the terrain
     * @param seed Seed of the world
     */
    void prepareFunctions(Random random, long seed);

    /**
     * Checks if functions have been prepared
     * @return Status of functions
     */
    boolean preparedFunctions();

    /**
     * Prepares the cache for the currently generating chunk
     * @param chunkX x coordinate of the chunk
     * @param chunkZ z coordinate of the chunk
     * @param modifierHandler Terrain modifier used to fetch local terrain modifier values
     */
    void prepareTerrainCache(int chunkX, int chunkZ, ModifierHandler modifierHandler);

    /**
     * Checks if everything is cached for the current chunk
     * @param chunkX x coordinate of the chunk
     * @param chunkZ z coordinate of the chunk
     * @return Status of the cache
     */
    boolean preparedTerrainCache(int chunkX, int chunkZ);

    /**
     * Gets the added terrain height within a chunk
     * @param localX x coordinate within the chunk
     * @param localZ z coordinate within the chunk
     * @return Height at the given coordinates
     */
    int getHeight(int localX, int localZ);

    /**
     * Checks if a block should be solid
     * @param localX x coordinate within the chunk
     * @param localY y coordinate relative to 2D terrain height
     * @param localZ z coordinate within the chunk
     * @return Status of the block
     */
    boolean isSolid(int localX, int localY, int localZ);

    /**
     * Gets the terrain modifier entry of the terrain feature
     * @return Terrain modifier entry
     */
    TerrainModifierEntry getEntry();
}
