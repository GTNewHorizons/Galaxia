package com.gtnewhorizons.galaxia.dimension.planets;

import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.dimension.PlanetEnum;

public abstract class BasePlanet {

    public DimensionDef buildDimension() {
        return DEF;
    }

    public DimensionDef getDef() {
        return DEF;
    }

    protected final DimensionDef DEF;

    protected BasePlanet() {
        DEF = createBuilder().build();
    }

    protected DimensionBuilder createBuilder() {
        return new DimensionBuilder().enumValue(getPlanetEnum())
            .provider(getProviderClass());
    }

    protected abstract PlanetEnum getPlanetEnum();

    protected abstract Class<? extends WorldProvider> getProviderClass();
}
