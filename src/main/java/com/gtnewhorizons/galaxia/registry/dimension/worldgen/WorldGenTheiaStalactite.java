package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

public class WorldGenTheiaStalactite extends WorldGenGalaxiaCave {
    private final Block stalactiteBlock;

    public WorldGenTheiaStalactite(int frequency, int minimumHeight, int maximumHeight, Block[] surfaceRequirements, Block stalactiteBlock) {
        super(frequency, minimumHeight, maximumHeight, surfaceRequirements);
        this.stalactiteBlock = stalactiteBlock;
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
            setBlockFast(world, x, y + yOffset, z, stalactiteBlock, 0);
        }
        return true;
    }
}
