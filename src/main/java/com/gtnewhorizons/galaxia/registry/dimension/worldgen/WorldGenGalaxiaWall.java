package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

public class WorldGenGalaxiaWall extends WorldGenGalaxiaBase {

    private final int rarity;
    private final Block[] wallRequirements;

    public WorldGenGalaxiaWall(int rarity, Block[] wallRequirements, Feature feature) {
        super(feature);
        this.rarity = rarity;
        this.wallRequirements = wallRequirements;
    }

    @Override
    public boolean stopGeneration(World world, Random random, int x, int y, int z) {
        if (random.nextInt(rarity) > 0) {
            return true;
        }
        net.minecraft.block.Block surfaceBlock = world.getBlock(x, y, z);
        for (Block surfaceRequirement : wallRequirements) {
            if (surfaceBlock == surfaceRequirement) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (stopGeneration(world, random, x, y, z)) {
            return false;
        }
        feature.generateFeature(world, random, x, y, z, wallRequirements);
        feature.finishGeneration();
        return true;
    }
}
