package com.gtnewhorizons.galaxia.registry.dimension.worldgen.locationrule;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.ChunkBoundedAccess;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.Feature;

/**
 * Places a feature inside a solid wall
 */
public class LocationRuleGalaxiaWall extends LocationRuleGalaxiaBase {

    private final int rarity;
    private final Block[] wallRequirements;
    private final int minimumHeight;
    private final int maximumHeight;

    public LocationRuleGalaxiaWall(int rarity, Block[] wallRequirements, Feature feature, int minimumHeight,
        int maximumHeight, boolean centered) {
        super(feature, centered);
        this.rarity = rarity;
        this.wallRequirements = wallRequirements;
        this.minimumHeight = minimumHeight;
        this.maximumHeight = maximumHeight;
    }

    public LocationRuleGalaxiaWall(int rarity, Block[] wallRequirements, Feature feature, int minimumHeight,
        int maximumHeight) {
        this(rarity, wallRequirements, feature, minimumHeight, maximumHeight, false);
    }

    @Override
    public boolean stopGeneration(World world, Random random, int x, int y, int z) {
        if (random.nextInt(rarity) > 0) {
            return true;
        }
        Block surfaceBlock = ChunkBoundedAccess.getBlock(world, x, y, z);
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
        return true;
    }

    public int getMinimumHeight() {
        return minimumHeight;
    }

    public int getMaximumHeight() {
        return maximumHeight;
    }
}
