package com.cleanroommc.galaxia.dimension;

import com.cleanroommc.galaxia.world.WorldProviderCalx;

public final class SolarSystemRegistry {
    public static DimensionDef CALX;

    public static void registerAll() {
        CALX = new DimensionBuilder()
            .name("calx")
            .id(666)
            .provider(WorldProviderCalx.class)
            .build();
    }
}
