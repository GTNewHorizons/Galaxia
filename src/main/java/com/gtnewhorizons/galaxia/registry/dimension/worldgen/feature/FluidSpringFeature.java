package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

public class FluidSpringFeature extends Feature {

    private final Block fluid;

    public FluidSpringFeature(Block fluid) {
        this.fluid = fluid;
    }

    @Override
    public void generateFeature(World world, Random random, int x, int y, int z, Block[] surfaceRequirements) {
        boolean validCeiling = false;
        Block ceilingBlock = ChunkBoundedAccess.getBlock(world, x, y + 1, z);
        for (Block surfaceRequirement : surfaceRequirements) {
            if (surfaceRequirement == ceilingBlock) {
                validCeiling = true;
                break;
            }
        }
        if (!validCeiling) {
            return;
        }
        boolean exposedSide = ChunkBoundedAccess.isAirBlock(world, x + 1, y, z);
        if (ChunkBoundedAccess.isAirBlock(world, x - 1, y, z)) {
            exposedSide = true;
        }
        if (ChunkBoundedAccess.isAirBlock(world, x, y, z + 1)) {
            exposedSide = true;
        }
        if (ChunkBoundedAccess.isAirBlock(world, x, y, z - 1)) {
            exposedSide = true;
        }
        if (!exposedSide) {
            return;
        }
        setBlockFast(world, x, y, z, fluid, 0);
    }
}
