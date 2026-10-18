package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;

/**
 * Separates stages. It belongs to the stage below it and is dropped together with that stage
 */
public class DecouplerPartDef extends RocketPartDef {

    public DecouplerPartDef(String key, int width, int height, int weight, String assetFolder) {
        super(key, width, height, weight, assetFolder);
    }

    @Override
    public RocketPartCategory category() {
        return RocketPartCategory.DECOUPLERS;
    }
}
