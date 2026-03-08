package com.gtnewhorizons.galaxia.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

public class RocketCoreModule extends RocketModule {

    private int tier;

    public RocketCoreModule(int id, String name, double height, double width, double weight, String modelName,
            int tier) {
        super(id, name, height, width, weight, modelName);
        this.tier = tier;
        ModuleRegistry.register(this);
    }

    public int getTier() {
        return tier;
    }

}
