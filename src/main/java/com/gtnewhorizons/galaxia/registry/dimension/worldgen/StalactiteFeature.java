package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

public class StalactiteFeature extends Feature {

    private final Block stalactiteBlock;

    public StalactiteFeature(Block stalactiteBlock) {
        this.stalactiteBlock = stalactiteBlock;
    }

    private void placeStalactite(World world, Random random, int x, int y, int z, Block[] surfaceRequirements) {
        if (!world.isAirBlock(x, y, z)) {
            return;
        }
        boolean validSurface = false;
        Block block = world.getBlock(x, y - 1, z);
        for (Block surfaceRequirement : surfaceRequirements) {
            if (block == surfaceRequirement) {
                validSurface = true;
                break;
            }
        }
        if (!validSurface) {
            return;
        }
        int height = random.nextInt(8) + 1;
        for (int yOffset = 0; yOffset < height; yOffset++) {
            if (!world.isAirBlock(x, y + yOffset, z)) {
                break;
            }
            setBlockFast(world, x, y + yOffset, z, stalactiteBlock, 0);
        }
    }

    @Override
    public void generateFeature(World world, Random random, int x, int y, int z, Block[] surfaceRequirements) {
        for (int clusterCount = 0; clusterCount < 16; clusterCount++) {
            placeStalactite(
                world,
                random,
                x + random.nextInt(5) - 4,
                y + random.nextInt(5) - 4,
                z + random.nextInt(5) - 4,
                surfaceRequirements);
        }
    }
}
