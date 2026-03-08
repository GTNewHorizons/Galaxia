package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import java.util.Random;

public class WorldGenCrystalCluster extends WorldGenGalaxiaCave {
    private final Block crystalBlock;

    public WorldGenCrystalCluster(int frequency, int minimumHeight, int maximumHeight, Block[] surfaceRequirements, Block crystalBlock) {
        super(frequency, minimumHeight, maximumHeight, surfaceRequirements);
        this.crystalBlock = crystalBlock;
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (!super.generate(world, random, x, y, z)) {
            return false;
        }
        for (int crystalCount = 0; crystalCount < random.nextInt(8) + 1; crystalCount++) {
            generateCrystal(world, random, x, y, z);
        }
        return true;
    }

    private void generateCrystal(World world, Random random, int x, int y, int z) {
        int height = random.nextInt(16) + 4;
        int straightness = random.nextInt(4) + 1;
        int straightnessIterator = 0;
        int xTilt = random.nextInt(3) - 1;
        int zTilt = random.nextInt(3) - 1;
        int xOffset = 0;
        int zOffset = 0;
        for (int yOffset = 0; yOffset < height; yOffset++) {
            straightnessIterator++;
            int combinedX = x + xOffset;
            int combinedY = y + yOffset;
            int combinedZ = z + zOffset;
            if (straightnessIterator >= straightness) {
                xOffset += xTilt;
                zOffset += zTilt;
                straightnessIterator = 0;
            }
            if (!world.isAirBlock(combinedX, combinedY, combinedZ) && world.getBlock(combinedX, combinedY, combinedZ) != crystalBlock) {
                return;
            }
            setBlockFast(world, combinedX, combinedY, combinedZ, crystalBlock, 0);
        }
    }
}
