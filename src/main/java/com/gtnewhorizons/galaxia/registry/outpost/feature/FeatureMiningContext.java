package com.gtnewhorizons.galaxia.registry.outpost.feature;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public record FeatureMiningContext(ModuleInstance module, PlanetaryFeatureKey feature, int coveredTiles,
    int totalTiles) {

    public FeatureMiningContext {
        if (coveredTiles <= 0 || totalTiles <= 0 || coveredTiles > totalTiles) {
            throw new IllegalArgumentException("Invalid feature coverage: " + coveredTiles + "/" + totalTiles);
        }
    }
}
