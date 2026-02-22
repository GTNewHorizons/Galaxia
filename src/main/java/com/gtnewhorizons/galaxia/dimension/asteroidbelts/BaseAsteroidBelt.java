package com.gtnewhorizons.galaxia.dimension.asteroidbelts;

import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.planets.BasePlanet;

public abstract class BaseAsteroidBelt extends BasePlanet {

    protected DimensionBuilder createBuilder() {
        return customizeDimension(
            new DimensionBuilder().enumValue(getPlanetEnum())
                .provider(getProviderClass())
                .airResistance(0)
                .gravity(0));
    }

    protected abstract Class<? extends WorldProvider> getProviderClass();
}
