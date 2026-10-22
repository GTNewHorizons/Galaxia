package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;

/**
 * Passenger cabin, adds seats besides the pilot's
 */
public class RiderPartDef extends RocketPartDef {

    private final int riderCapacity;

    public RiderPartDef(String key, int width, int height, int weight, int riderCapacity, String assetFolder) {
        super(key, width, height, weight, assetFolder);
        this.riderCapacity = riderCapacity;
    }

    public int riderCapacity() {
        return riderCapacity;
    }

    @Override
    public RocketPartCategory category() {
        return RocketPartCategory.CABINS;
    }
}
