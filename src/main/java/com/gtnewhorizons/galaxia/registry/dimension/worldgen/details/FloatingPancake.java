package com.gtnewhorizons.galaxia.registry.dimension.worldgen.details;

import java.util.Random;

public class FloatingPancake implements Terrain3d {

    private static final int CHUNK_AREA = 256;

    private final int[] pancakeThickness = new int[256];

    private Random random;
    private int currentX;
    private int currentZ;
    private boolean needsConfirmation = true;

    @Override
    public void prepareFunctions(Random random) {
        this.random = random;
    }

    @Override
    public boolean preparedFunctions() {
        return this.random != null;
    }

    @Override
    public void prepareTerrainCache(int chunkX, int chunkZ) {
        currentX = chunkX;
        currentZ = chunkZ;
        for (int i = 0; i < CHUNK_AREA; i++) {
            pancakeThickness[i] = 1 + random.nextInt(4);
        }
    }

    @Override
    public boolean preparedTerrainCache(int chunkX, int chunkZ) {
        if (needsConfirmation) {
            needsConfirmation = false;
            return false;
        }
        return chunkX == currentX && chunkZ == currentZ;
    }

    @Override
    public int getHeight(int localX, int localZ) {
        return 4 + pancakeThickness[localX + localZ * 16];
    }

    @Override
    public boolean isSolid(int localX, int localY, int localZ) {
        return localY > 4;
    }
}
