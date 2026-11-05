package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;

/**
 * Module that adds a capability to the rocket rather than structure or propulsion
 */
public class FunctionalPartDef extends RocketPartDef {

    public FunctionalPartDef(String key, int width, int height, int weight, String assetFolder) {
        super(key, width, height, weight, assetFolder);
    }

    @Override
    public RocketPartCategory category() {
        return RocketPartCategory.STRUCTURAL;
    }
}
