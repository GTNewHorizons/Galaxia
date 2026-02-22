package com.gtnewhorizons.galaxia.dimension;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A WorldProvider class specific to Galaxia Dimensions
 */
public class WorldProviderGalaxia extends WorldProviderSpace {

    private static final Map<Integer, Consumer<WorldProviderBuilder>> CONFIGS = new ConcurrentHashMap<>();

    /**
     * Registers the configurator for the dimension
     * @param dimensionId The ID of the dimension to set the configurator to
     * @param configurator The configurator to bind to the planet
     */
    public static void registerConfigurator(int dimensionId, Consumer<WorldProviderBuilder> configurator) {
        CONFIGS.put(dimensionId, configurator);
    }

    public WorldProviderGalaxia() {}

    /**
     * Sets the dimension of the world provider
     * @param dimensionId Dimension ID to set to
     */
    @Override
    public void setDimension(int dimensionId) {
        super.setDimension(dimensionId);

        // Pull WorldProviderBuilder from configs and build
        Consumer<WorldProviderBuilder> config = CONFIGS.get(dimensionId);
        if (config != null) {
            WorldProviderBuilder builder = WorldProviderBuilder.configure(this);
            config.accept(builder);
            builder.build();
        }
    }
}
