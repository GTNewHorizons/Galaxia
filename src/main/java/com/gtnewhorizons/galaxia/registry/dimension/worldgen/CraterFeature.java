package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

public class CraterFeature extends Feature {

    private final Block tektite;

    public CraterFeature(Block tektite) {
        this.tektite = tektite;
    }

    @Override
    public void generateFeature(World world, Random random, int x, int y, int z, Block[] surfaceRequirements) {
        int diameter = 16 + random.nextInt(16);
        int radius = diameter / 2;
        int squaredCraterRadius = radius * radius;
        int heightOffset = radius / 2;

        for (int localX = -radius; localX <= radius; localX++) {
            for (int localZ = -radius; localZ <= radius; localZ++) {
                double rimDistance = localX * localX + localZ * localZ;
                for (int rimY = -10; rimY <= 10; rimY++) {
                    if (rimDistance >= squaredCraterRadius - random.nextInt(96)
                        && rimDistance < squaredCraterRadius + random.nextInt(64)
                        && !world.isAirBlock(x + localX, y + rimY + heightOffset, z + localZ)
                        && world.isAirBlock(x + localX, y + rimY + heightOffset + 1, z + localZ)) {
                        setBlockFast(world, x + localX, y + rimY + heightOffset + 1, z + localZ, tektite, 0);
                        break;
                    }
                }
                for (int localY = -radius; localY <= radius; localY++) {
                    if (world.isAirBlock(x + localX, y + localY + heightOffset, z + localZ)) continue;
                    double squaredDistance = rimDistance + localY * localY;
                    if (squaredDistance < squaredCraterRadius * (1.0 - random.nextDouble() * 0.1)) {
                        int wx = localX + x, wy = localY + y + heightOffset, wz = localZ + z;
                        setBlockFast(world, wx, wy, wz, Blocks.air, 0);
                    }
                }
            }
        }
    }
}
