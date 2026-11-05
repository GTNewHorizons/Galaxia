package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;

/**
 * Crewed command module, seats the pilot
 */
public class CapsulePartDef extends RocketPartDef {

    public CapsulePartDef(String key, int width, int height, int weight, String assetFolder) {
        super(key, width, height, weight, assetFolder);
    }

    @Override
    public RocketPartCategory category() {
        return RocketPartCategory.CAPSULES;
    }
}
