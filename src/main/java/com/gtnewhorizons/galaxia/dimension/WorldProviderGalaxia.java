package com.gtnewhorizons.galaxia.dimension;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class WorldProviderGalaxia extends WorldProviderSpace {

    private static final Map<Integer, Consumer<WorldProviderBuilder>> CONFIGS = new ConcurrentHashMap<>();

    public static void registerConfigurator(int dimensionId, Consumer<WorldProviderBuilder> configurator) {
        CONFIGS.put(dimensionId, configurator);
    }

    public WorldProviderGalaxia() {}

    @Override
    public void setDimension(int dimensionId) {
        super.setDimension(dimensionId);

        Consumer<WorldProviderBuilder> config = CONFIGS.get(dimensionId);
        if (config != null) {
            WorldProviderBuilder builder = WorldProviderBuilder.configure(this);
            config.accept(builder);
            builder.build();
        }
    }
}
