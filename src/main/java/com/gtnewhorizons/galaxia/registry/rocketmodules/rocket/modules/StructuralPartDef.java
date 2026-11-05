package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;

/**
 * Plain structure without any function
 */
public class StructuralPartDef extends RocketPartDef {

    public StructuralPartDef(String key, int width, int height, int weight, String assetFolder) {
        super(key, width, height, weight, assetFolder);
    }

    @Override
    public RocketPartCategory category() {
        return RocketPartCategory.STRUCTURAL;
    }
}
