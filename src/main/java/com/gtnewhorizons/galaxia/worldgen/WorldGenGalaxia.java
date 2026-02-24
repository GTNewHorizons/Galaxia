package com.gtnewhorizons.galaxia.worldgen;

import com.gtnewhorizons.galaxia.utility.BlockMeta;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;

public abstract class WorldGenGalaxia extends WorldGenerator {
    private final int rarity;
    private final BlockMeta surfaceRequirement;

    public WorldGenGalaxia(int rarity, BlockMeta surfaceRequirement) {
        super();
        this.rarity = rarity;
        this.surfaceRequirement = surfaceRequirement;
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (random.nextInt(rarity) > 0) {
            return false;
        }
        Block surfaceBlock = world.getBlock(x, y - 1, z);
        int surfaceMeta = world.getBlockMetadata(x, y - 1, z);
        return surfaceBlock == surfaceRequirement.block() && surfaceMeta == surfaceRequirement.meta();
    }
}
