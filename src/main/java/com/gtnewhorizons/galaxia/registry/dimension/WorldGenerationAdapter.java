package com.gtnewhorizons.galaxia.registry.dimension;

import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;

@FunctionalInterface
public interface WorldGenerationAdapter {

    WorldGenerationAdapter NONE = builder -> {};

    static WorldGenerationAdapter none() {
        return NONE;
    }

    void configure(WorldProviderBuilder builder);
}
