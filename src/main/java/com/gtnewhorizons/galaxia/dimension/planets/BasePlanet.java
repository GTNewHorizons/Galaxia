package com.gtnewhorizons.galaxia.dimension.planets;

import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.dimension.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.dimension.WorldProviderGalaxia;

public abstract class BasePlanet {

    protected final DimensionDef DEF;

    protected BasePlanet() {
        DEF = createBuilder().build();
    }

    protected DimensionBuilder createBuilder() {
        DimensionEnum planet = getPlanetEnum();

        WorldProviderGalaxia.registerConfigurator(planet.getId(), this::configureProvider);

        return customizeDimension(
            new DimensionBuilder().enumValue(planet)
                .provider(WorldProviderGalaxia.class));
    }

    protected DimensionBuilder customizeDimension(DimensionBuilder builder) {
        return builder;
    }

    protected void configureProvider(WorldProviderBuilder builder) {
        builder.sky(true);
    }

    public DimensionDef getDef() {
        return DEF;
    }

    public abstract DimensionEnum getPlanetEnum();
}
