package com.gtnewhorizons.galaxia.registry.dimension.worldgen.details;

import java.util.Random;

public interface Terrain3d {

    void prepareFunctions(Random random);

    boolean preparedFunctions();

    void prepareTerrainCache(int chunkX, int chunkZ);

    boolean preparedTerrainCache(int chunkX, int chunkZ);

    int getHeight(int localX, int localZ);

    boolean isSolid(int localX, int localY, int localZ);
}
