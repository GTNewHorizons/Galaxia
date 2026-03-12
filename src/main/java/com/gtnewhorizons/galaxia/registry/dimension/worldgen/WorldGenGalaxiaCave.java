package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

public class WorldGenGalaxiaCave extends WorldGenGalaxiaSurface {

    private final int frequency;
    private final int minimumHeight;
    private final int maximumHeight;

    public WorldGenGalaxiaCave(int frequency, int minimumHeight, int maximumHeight, Block[] surfaceRequirements,
        Feature feature) {
        super(1, surfaceRequirements, feature);
        this.frequency = frequency;
        this.minimumHeight = minimumHeight;
        this.maximumHeight = maximumHeight;
    }

    @Override
    public boolean stopGeneration(World world, Random random, int x, int y, int z) {
        if (super.stopGeneration(world, random, x, y, z)) {
            return true;
        }
        return !world.isAirBlock(x, y, z);
    }

    public int getFrequency() {
        return frequency;
    }

    public int getMinimumHeight() {
        return minimumHeight;
    }

    public int getMaximumHeight() {
        return maximumHeight;
    }
}
