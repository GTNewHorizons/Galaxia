package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

public class WorldGenGalaxiaCave extends WorldGenGalaxiaSurface {

    private final int frequency;
    private final int maximumHeight;

    public WorldGenGalaxiaCave(int frequency, int maximumHeight, Block[] surfaceRequirements) {
        super(1, surfaceRequirements);
        this.frequency = frequency;
        this.maximumHeight = maximumHeight;
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (!super.generate(world, random, x, y, z)) {
            return false;
        }
        return world.isAirBlock(x, y, z);
    }

    public int getFrequency() {
        return frequency;
    }

    public int getMaximumHeight() {
        return maximumHeight;
    }
}
