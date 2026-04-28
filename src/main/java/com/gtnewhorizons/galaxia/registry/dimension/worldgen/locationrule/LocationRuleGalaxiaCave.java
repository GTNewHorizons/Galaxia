package com.gtnewhorizons.galaxia.registry.dimension.worldgen.locationrule;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.Feature;

/**
 * Places a feature on the surface in a cave layer
 */
public class LocationRuleGalaxiaCave extends LocationRuleGalaxiaSurface {

    private final int frequency;
    private final int minimumHeight;
    private final int maximumHeight;

    public LocationRuleGalaxiaCave(int frequency, int minimumHeight, int maximumHeight, Block[] surfaceRequirements,
        Feature feature, boolean centered) {
        super(1, surfaceRequirements, feature, centered);
        this.frequency = frequency;
        this.minimumHeight = minimumHeight;
        this.maximumHeight = maximumHeight;
    }

    public LocationRuleGalaxiaCave(int frequency, int minimumHeight, int maximumHeight, Block[] surfaceRequirements,
        Feature feature) {
        this(frequency, minimumHeight, maximumHeight, surfaceRequirements, feature, false);
    }

    @Override
    public boolean stopGeneration(World world, Random random, int x, int y, int z) {
        if (super.stopGeneration(world, random, x, y, z)) {
            return true;
        }
        return !world.isAirBlock(x, y, z);
    }

    public int getFrequency() {
        return frequency;
    }

    public int getMinimumHeight() {
        return minimumHeight;
    }

    public int getMaximumHeight() {
        return maximumHeight;
    }
}
