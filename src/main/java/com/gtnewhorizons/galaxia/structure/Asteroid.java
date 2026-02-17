package com.gtnewhorizons.galaxia.structure;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;

public class Asteroid extends WorldGenerator {
    private final int minimumSize;
    private final int maximumSize;
    private final int rarity;
    private final Block fillerBlock;
    private final int fillerBlockMeta;

    public Asteroid(int minimumSize, int maximumSize, int rarity, Block fillerBlock, int fillerBlockMeta) {
        this.minimumSize = minimumSize;
        this.maximumSize = maximumSize;
        this.rarity = rarity;
        this.fillerBlock = fillerBlock;
        this.fillerBlockMeta = fillerBlockMeta;
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (random.nextInt(rarity) > 0) {
            return false;
        }

        int size = minimumSize;
        int variation = maximumSize - minimumSize;
        if (variation > 0) {
            size += random.nextInt(variation);
        }

        int radius = size / 2;
        for (int xOffset = -radius; xOffset <= radius; xOffset++) {
            for (int yOffset = -radius; yOffset <= radius; yOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    world.setBlock(x + xOffset, y + yOffset, z + zOffset, fillerBlock, fillerBlockMeta, 2);
                }
            }
        }
        return true;
    }
}
