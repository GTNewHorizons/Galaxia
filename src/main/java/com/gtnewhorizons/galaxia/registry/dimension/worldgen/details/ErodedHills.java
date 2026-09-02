package com.gtnewhorizons.galaxia.registry.dimension.worldgen.details;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.modifier.ModifierHandler;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.modifier.TerrainModifierEntry;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.NoiseSampler3D;

import java.util.Random;

/**
 * Creates hills with many overhangs using 3D Perlin noise
 */
public class ErodedHills implements Terrain3D {
    private static final double SCALE = 0.025;

    private final int height;
    private final TerrainModifierEntry modifierEntry;
    private final boolean[] blockColumn;

    private int currentX;
    private int currentZ;
    private NoiseSampler3D hillNoise;
    private double[] modifierValues;
    private boolean needsConfirmation = true;

    /**
     * Constructor for configuring the 3D terrain feature
     * @param modifierEntry Modifier entry with a modifier range to determine the terrain presence
     * @param height Maximum height of the hills
     */
    public ErodedHills(TerrainModifierEntry modifierEntry, int height) {
        this.modifierEntry = modifierEntry;
        this.height = height;
        blockColumn = new boolean[height];
    }

    @Override
    public void prepareFunctions(Random random, long seed) {
        hillNoise = new NoiseSampler3D(seed, 4);
    }

    @Override
    public boolean preparedFunctions() {
        return hillNoise != null;
    }

    @Override
    public void prepareTerrainCache(int chunkX, int chunkZ, ModifierHandler modifierHandler) {
        modifierValues = modifierHandler.assignModifierValues(getEntry(), chunkX, chunkZ);
        currentX = chunkX;
        currentZ = chunkZ;
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
        // Multiply by 8 to make transitions narrower and increase presence
        double localModifier = Math.min(modifierValues[localX + localZ * 16] * 8, 1);
        if (localModifier <= 0.01) {
            return 0;
        }
        // Get the highest block within the local column to correctly place the surface layer
        for (int i = 0; i < blockColumn.length; i++) {
            blockColumn[i] = hillNoise.samplePoint(localX + (currentX << 4), i, localZ + (currentZ << 4), SCALE, SCALE, SCALE) > 0;
        }
        int localHeight = height;
        boolean foundBlock = false;
        for (int i = height - 1; i >= 0; i--) {
            if (blockColumn[i]) {
                localHeight = i;
                foundBlock = true;
                break;
            }
        }
        // Force height to 0 if there are no blocks
        if (!foundBlock) {
            return 0;
        }
        return (int) (localHeight * localModifier);
    }

    @Override
    public boolean isSolid(int localX, int localY, int localZ) {
        if (localY > blockColumn.length) {
            return false;
        }
        return blockColumn[localY];
    }

    @Override
    public TerrainModifierEntry getEntry() {
        return modifierEntry;
    }
}
