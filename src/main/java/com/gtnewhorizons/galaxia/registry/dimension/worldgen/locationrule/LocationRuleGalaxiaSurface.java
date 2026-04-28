package com.gtnewhorizons.galaxia.registry.dimension.worldgen.locationrule;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.Feature;

/**
 * Places a feature on the planet's surface
 */
public class LocationRuleGalaxiaSurface extends LocationRuleGalaxiaBase {

    private final int rarity;
    private final Block[] surfaceRequirements;

    public LocationRuleGalaxiaSurface(int rarity, Block[] surfaceRequirements, Feature feature, boolean centered) {
        super(feature, centered);
        this.rarity = rarity;
        this.surfaceRequirements = surfaceRequirements;
    }

    public LocationRuleGalaxiaSurface(int rarity, Block[] surfaceRequirements, Feature feature) {
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
