package com.gtnewhorizons.galaxia.registry.dimension.cave;

import java.util.Random;

public interface CaveShape {

    void prepareCaveShape(Random random);

    boolean preparedCaveShape();

    void prepareCaveCache(int chunkX, int chunkZ);

    boolean preparedCaveCache(int chunkX, int chunkZ);

    boolean generateCave(int localX, int localY, int localZ, int height);
}
