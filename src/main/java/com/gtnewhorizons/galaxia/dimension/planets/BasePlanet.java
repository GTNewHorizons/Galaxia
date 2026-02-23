package com.gtnewhorizons.galaxia.dimension.planets;

import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.dimension.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.dimension.WorldProviderGalaxia;

/**
 * An abstract class that all planets should derive from
 */
public abstract class BasePlanet {

    // Conversion constant to convert Earth Radii to AU
    public static final double earthRadiusToAU = 23481;

    protected final DimensionDef DEF;

    /**
     * Create a dimension def on instantiation of super object
     */
    protected BasePlanet() {
        DEF = createBuilder().build();
    }

    /**
     * Creates a Dimension Builder to add effects and fields to more simply
     * 
     * @return The dimension builder configured with the planet enum etc.
     */
    protected DimensionBuilder createBuilder() {
        DimensionEnum planet = getPlanetEnum();

        WorldProviderGalaxia.registerConfigurator(planet.getId(), this::configureProvider);

        return customizeDimension(
            new DimensionBuilder().enumValue(planet)
                .provider(WorldProviderGalaxia.class));
    }

    /**
     * The start point of any building chain
     * 
     * @param builder The dimension builder to chain on
     * @return The dimension builder ready for chaining
     */
    protected DimensionBuilder customizeDimension(DimensionBuilder builder) {
        return builder;
    }

    /**
     * Configures the WorldProviderBuilder
     * 
     * @param builder The world provider builder being configured
     */
    protected void configureProvider(WorldProviderBuilder builder) {
        builder.sky(true);
    }

    /**
     * Getter for DimensionDef
     * 
     * @return DimensionDef
     */
    public DimensionDef getDef() {
        return DEF;
    }

    /**
     * Abstract method to ensure all planets have a method to get the Enum
     * 
     * @return DimensionEnum of planet instance
     */
    public abstract DimensionEnum getPlanetEnum();
}
