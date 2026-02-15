package com.cleanroommc.galaxia.dimension;

import net.minecraft.world.WorldProvider;

public final class DimensionDef {

    public final String name;
    public final int id;
    public final Class<? extends WorldProvider> provider;
    public final boolean keepLoaded;
    public final double gravity;

    DimensionDef(
        String name,
        int id,
        Class<? extends WorldProvider> provider,
        boolean keepLoaded,
        double gravity
    ) {
        this.name = name;
        this.id = id;
        this.provider = provider;
        this.keepLoaded = keepLoaded;
        this.gravity = gravity;
    }
}
