package com.gtnewhorizons.galaxia.registry.dimension.worldgen.locationrule;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.Feature;

/**
 * Provides primary logic for location rules.
 * Allows features to be placed in specific areas.
 */
public abstract class LocationRuleGalaxiaBase extends WorldGenerator {

    private final boolean centered;
    protected final Feature feature;

    public LocationRuleGalaxiaBase(Feature feature, boolean centered) {
        this.feature = feature;
        this.centered = centered;
    }

    public abstract boolean stopGeneration(World world, Random random, int x, int y, int z);

    public Feature getFeature() {
        return feature;
    }

    public boolean isCentered() {
        return centered;
    }
}
