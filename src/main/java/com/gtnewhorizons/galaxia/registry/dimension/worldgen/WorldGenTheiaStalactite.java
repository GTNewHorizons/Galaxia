package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

public class WorldGenTheiaStalactite extends WorldGenGalaxiaCave {

    public WorldGenTheiaStalactite(int frequency, Block[] surfaceRequirements) {
        super(frequency, 32, surfaceRequirements);
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (!super.generate(world, random, x, y, z)) {
            return false;
        }
        int height = random.nextInt(8) + 1;
        for (int yOffset = 0; yOffset < height; yOffset++) {
            if (!world.isAirBlock(x, y + yOffset, z)) {
                break;
            }
            setBlockFast(world, x, y + yOffset, z, Blocks.redstone_block, 0);
        }
        return true;
    }
}
