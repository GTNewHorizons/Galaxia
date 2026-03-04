package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import java.util.Random;

public class WorldGenMagmaZone extends WorldGenGalaxiaSurface {
    private final Block magmaBlock;
    private final Block obsidianBlock;
    private final int oceanHeight;

    public WorldGenMagmaZone(int rarity, Block[] surfaceRequirements, Block magmaBlock, Block obsidianBlock, int oceanHeight) {
        super(rarity, surfaceRequirements);
        this.magmaBlock = magmaBlock;
        this.obsidianBlock = obsidianBlock;
        this.oceanHeight = oceanHeight;
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        y = oceanHeight;
        int minZ = -(random.nextInt(8) + 4);
        int maxZ = random.nextInt(8) + 4;
        for (int xOffset = -(random.nextInt(8) + 4); xOffset <= random.nextInt(8) + 4; xOffset++) {
            for (int zOffset = minZ; zOffset <= maxZ; zOffset++) {
                for (int yOffset = 0; yOffset >= -32; yOffset--) {
                    if (random.nextInt(16) > -yOffset) {
                        continue;
                    }
                    if (world.getBlock(x + xOffset, y + yOffset, z + zOffset) != obsidianBlock) {
                        continue;
                    }
                    setBlockFast(world, x + xOffset, y + yOffset, z + zOffset, magmaBlock, 0);
                }
            }
        }
        return true;
    }
}
