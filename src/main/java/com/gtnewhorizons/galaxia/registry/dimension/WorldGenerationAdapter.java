package com.gtnewhorizons.galaxia.registry.dimension;

import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;

@FunctionalInterface
public interface WorldGenerationAdapter {

    void configure(WorldProviderBuilder builder);
}
