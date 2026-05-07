package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

public class GeodeFeature extends Feature {

    private final Block shell;
    private final Block crystal;

    public GeodeFeature(Block shell, Block crystal) {
        this.shell = shell;
        this.crystal = crystal;
    }

    @Override
    public void generateFeature(World world, Random random, int x, int y, int z, Block[] surfaceRequirements) {
        int size = 4 + random.nextInt(5);
        int squaredSize = size * size;
        for (int xOffset = -size; xOffset <= size; xOffset++) {
            int combinedX = x + xOffset;
            for (int yOffset = -size; yOffset <= size; yOffset++) {
                int combinedY = y + yOffset;
                for (int zOffset = -size; zOffset <= size; zOffset++) {
                    int squaredRadius = xOffset * xOffset + yOffset * yOffset + zOffset * zOffset + random.nextInt(16);
                    if (squaredRadius > squaredSize) {
                        continue;
                    }
                    int combinedZ = z + zOffset;
                    if (ChunkBoundedAccess.isAirBlock(world, combinedX, combinedY, combinedZ)) {
                        continue;
                    }
                    int radiusDifference = squaredSize - squaredRadius;
                    if (radiusDifference < 16) {
                        setBlockFast(world, combinedX, combinedY, combinedZ, shell, 0);
                    } else if (radiusDifference < 32) {
                        setBlockFast(world, combinedX, combinedY, combinedZ, crystal, 0);
                    } else {
                        setBlockFast(world, combinedX, combinedY, combinedZ, Blocks.air, 0);
                    }
                }
            }
        }
    }
}
