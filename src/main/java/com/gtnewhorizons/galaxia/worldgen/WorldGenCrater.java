package com.gtnewhorizons.galaxia.worldgen;

import java.util.Random;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.utility.BlockMeta;

public class WorldGenCrater extends WorldGenGalaxia {

    public WorldGenCrater(int rarity, BlockMeta[] surfaceRequirements) {
        super(rarity, surfaceRequirements);
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (!super.generate(world, random, x, y, z)) {
            return false;
        }
        int diameter = 16 + random.nextInt(16);
        int radius = diameter / 2;
        int squaredCraterRadius = radius * radius;
        int heightOffset = radius / 2;
        for (int localX = -radius; localX <= radius; localX++) {
            for (int localY = -radius; localY <= radius; localY++) {
                for (int localZ = -radius; localZ <= radius; localZ++) {
                    if (world.isAirBlock(x + localX, y + localY + heightOffset, z + localZ)) continue;
                    double squaredDistance = localX * localX + localY * localY + localZ * localZ;
                    if (squaredDistance < squaredCraterRadius * (1.0 - random.nextDouble() * 0.1)) {
                        setBlockFast(world, localX + x, localY + y + heightOffset, localZ + z, Blocks.air, 0);
                    }
                }
            }
        }
        return true;
    }
}
