package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;

/**
 * Command module that can touch down without a silo at the destination
 */
public class LanderPartDef extends RocketPartDef {

    public LanderPartDef(String key, int width, int height, int weight, String assetFolder) {
        super(key, width, height, weight, assetFolder);
    }

    @Override
    public RocketPartCategory category() {
        return RocketPartCategory.CABINS;
    }
}
