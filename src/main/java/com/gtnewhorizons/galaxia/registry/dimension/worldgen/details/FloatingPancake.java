package com.gtnewhorizons.galaxia.registry.dimension.worldgen.details;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainModifierEntry;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.modifier.ModifierHandler;

import java.util.Random;

/**
 * Test 3D terrain with creates some white noise suspended in the air
 */
public class FloatingPancake implements Terrain3D {

    private static final int CHUNK_AREA = 256;

    private final int[] pancakeThickness = new int[256];
    private final TerrainModifierEntry modifierEntry;

    private Random random;
    private int currentX;
    private int currentZ;
    private boolean needsConfirmation = true;
    private double[] modifierValues;

    public FloatingPancake(TerrainModifierEntry modifierEntry) {
        this.modifierEntry = modifierEntry;
    }

    @Override
    public void prepareFunctions(Random random, long seed) {
        this.random = random;
    }

    @Override
    public boolean preparedFunctions() {
        return this.random != null;
    }

    @Override
    public void prepareTerrainCache(int chunkX, int chunkZ, ModifierHandler modifierHandler) {
        currentX = chunkX;
        currentZ = chunkZ;
        modifierValues = modifierHandler.assignModifierValues(getEntry(), chunkX, chunkZ);
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
        if (modifierValues[localX + localZ * 16] <= 0.01) {
            return 0;
        }
        return 4 + pancakeThickness[localX + localZ * 16];
    }

    @Override
    public boolean isSolid(int localX, int localY, int localZ) {
        return localY > 4;
    }

    @Override
    public TerrainModifierEntry getEntry() {
        return modifierEntry;
    }
}
