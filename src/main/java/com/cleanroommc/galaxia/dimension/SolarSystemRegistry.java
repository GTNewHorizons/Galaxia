package com.cleanroommc.galaxia.dimension;

import com.cleanroommc.galaxia.world.BiomeGenCalx;
import com.cleanroommc.galaxia.world.WorldProviderCalx;

public class SolarSystemRegistry {
    public static void registerAll() {
        new DimensionBuilder()
            .name("Calx")
            .id(666)
            .provider(WorldProviderCalx.class)
            .biome(() -> new BiomeGenCalx(150))
            .dimensionId(666)
            .build();
    }
}
