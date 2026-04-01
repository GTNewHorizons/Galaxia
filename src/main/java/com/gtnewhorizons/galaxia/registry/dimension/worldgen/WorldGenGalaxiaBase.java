package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

public abstract class WorldGenGalaxiaBase extends WorldGenerator {

    private final boolean centered;
    protected final Feature feature;

    public WorldGenGalaxiaBase(Feature feature, boolean centered) {
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
