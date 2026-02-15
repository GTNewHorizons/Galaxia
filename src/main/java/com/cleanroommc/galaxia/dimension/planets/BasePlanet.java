package com.cleanroommc.galaxia.dimension.planets;

import net.minecraft.world.WorldProvider;

import com.cleanroommc.galaxia.dimension.DimensionBuilder;
import com.cleanroommc.galaxia.dimension.DimensionDef;
import com.cleanroommc.galaxia.dimension.PlanetEnum;
import com.cleanroommc.galaxia.utility.IPlanet;

public abstract class BasePlanet implements IPlanet {

    @Override
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
