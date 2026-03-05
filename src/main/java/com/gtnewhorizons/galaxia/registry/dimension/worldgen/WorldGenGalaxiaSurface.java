package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

public abstract class WorldGenGalaxiaSurface extends WorldGenGalaxiaBase {

    private final int rarity;
    private final Block[] surfaceRequirements;

    public WorldGenGalaxiaSurface(int rarity, Block[] surfaceRequirements) {
        super();
        this.rarity = rarity;
        this.surfaceRequirements = surfaceRequirements;
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (random.nextInt(rarity) > 0) {
            return false;
        }
        net.minecraft.block.Block surfaceBlock = world.getBlock(x, y - 1, z);
        for (Block surfaceRequirement : surfaceRequirements) {
            if (surfaceBlock == surfaceRequirement) {
                return true;
            }
        }
        return false;
    }
}
