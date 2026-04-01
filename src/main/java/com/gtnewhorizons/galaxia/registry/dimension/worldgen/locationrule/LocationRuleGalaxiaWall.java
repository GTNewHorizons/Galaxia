package com.gtnewhorizons.galaxia.registry.dimension.worldgen.locationrule;

import java.util.Random;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.Feature;
import net.minecraft.block.Block;
import net.minecraft.world.World;

public class LocationRuleGalaxiaWall extends LocationRuleGalaxiaBase {

    private final int rarity;
    private final Block[] wallRequirements;

    public LocationRuleGalaxiaWall(int rarity, Block[] wallRequirements, Feature feature, boolean centered) {
        super(feature, centered);
        this.rarity = rarity;
        this.wallRequirements = wallRequirements;
    }

    public LocationRuleGalaxiaWall(int rarity, Block[] wallRequirements, Feature feature) {
        this(rarity, wallRequirements, feature, false);
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
