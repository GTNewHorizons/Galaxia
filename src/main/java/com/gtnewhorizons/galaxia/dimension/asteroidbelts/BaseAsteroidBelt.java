package com.gtnewhorizons.galaxia.dimension.asteroidbelts;

import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.dimension.PlanetEnum;

public abstract class BaseAsteroidBelt {

    public DimensionDef buildDimension() {
        return DEF;
    }

    public DimensionDef getDef() {
        return DEF;
    }

    protected final DimensionDef DEF;

    protected BaseAsteroidBelt() {
        DEF = createBuilder().build();
    }

    protected DimensionBuilder createBuilder() {
        return new DimensionBuilder().enumValue(getPlanetEnum())
            .provider(getProviderClass())
            .airResistance(0)
            .gravity(0);
    }

    protected abstract PlanetEnum getPlanetEnum();

    protected abstract Class<? extends WorldProvider> getProviderClass();
}
