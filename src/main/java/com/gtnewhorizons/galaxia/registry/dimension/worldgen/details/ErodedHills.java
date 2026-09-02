package com.gtnewhorizons.galaxia.registry.dimension.worldgen.details;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainModifierEntry;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.modifier.ModifierHandler;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.NoiseSampler3D;

import java.util.Random;

public class ErodedHills implements Terrain3D {
    private static final double SCALE = 0.025;

    private final int height;
    private final TerrainModifierEntry modifierEntry;

    private int currentX;
    private int currentZ;
    private NoiseSampler3D hillNoise;
    private double[] modifierValues;

    public ErodedHills(TerrainModifierEntry modifierEntry, int height) {
        this.modifierEntry = modifierEntry;
        this.height = height;
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
        return chunkX == currentX && chunkZ == currentZ;
    }

    @Override
    public int getHeight(int localX, int localZ) {
        if (modifierValues[localX + localZ * 16] <= 0.01) {
            return 0;
        }
        return height;
    }

    @Override
    public boolean isSolid(int localX, int localY, int localZ) {
        double sampledValue = hillNoise.samplePoint(localX + (currentX << 4), localY, localZ + (currentZ << 4), SCALE, SCALE, SCALE);
        return sampledValue > 0;
    }

    @Override
    public TerrainModifierEntry getEntry() {
        return modifierEntry;
    }
}
