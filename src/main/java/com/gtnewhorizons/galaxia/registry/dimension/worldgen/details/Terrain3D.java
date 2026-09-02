package com.gtnewhorizons.galaxia.registry.dimension.worldgen.details;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainModifierEntry;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.modifier.ModifierHandler;

import java.util.Random;

public interface Terrain3D {

    void prepareFunctions(Random random, long seed);

    boolean preparedFunctions();

    void prepareTerrainCache(int chunkX, int chunkZ, ModifierHandler modifierHandler);

    boolean preparedTerrainCache(int chunkX, int chunkZ);

    int getHeight(int localX, int localZ);

    boolean isSolid(int localX, int localY, int localZ);

    TerrainModifierEntry getEntry();
}
