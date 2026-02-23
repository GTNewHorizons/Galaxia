package com.gtnewhorizons.galaxia.dimension.asteroidbelts;

import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.dimension.builder.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.planets.BasePlanet;

/**
 * A basic abstract class setting some default values for all asteroid belts
 */
public abstract class BaseAsteroidBelt extends BasePlanet {

    /**
     * Creates a dimension builder with some default values
     *
     * @return A DimensionBuilder with some universal defaults for all asteroid belts
     */
    protected DimensionBuilder createBuilder() {
        return customizeDimension(
            new DimensionBuilder().enumValue(getPlanetEnum())
                .provider(getProviderClass())
                .airResistance(0)
                .gravity(0));
    }

    protected abstract Class<? extends WorldProvider> getProviderClass();
}
