package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

public class WorldGenGalaxiaSurface extends WorldGenGalaxiaBase {

    private final int rarity;
    private final Block[] surfaceRequirements;

    public WorldGenGalaxiaSurface(int rarity, Block[] surfaceRequirements, Feature feature, boolean centered) {
        super(feature, centered);
        this.rarity = rarity;
        this.surfaceRequirements = surfaceRequirements;
    }

    public WorldGenGalaxiaSurface(int rarity, Block[] surfaceRequirements, Feature feature) {
        this(rarity, surfaceRequirements, feature, false);
    }

    @Override
    public boolean stopGeneration(World world, Random random, int x, int y, int z) {
        if (random.nextInt(rarity) > 0) {
            return true;
        }
        net.minecraft.block.Block surfaceBlock = world.getBlock(x, y - 1, z);
        for (Block surfaceRequirement : surfaceRequirements) {
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
        feature.generateFeature(world, random, x, y, z, surfaceRequirements);
        feature.finishGeneration();
        return true;
    }
}
