package com.gtnewhorizons.galaxia.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.EnumModuleCategory;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.EnumTiers;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

public class RocketCoreModule extends RocketModule {

    private EnumTiers tier;

    public RocketCoreModule(int id, String name, double height, double width, double weight, String modelName,
        EnumTiers tier) {
        super(id, name, height, width, weight, modelName);
        this.tier = tier;
        setCategory(EnumModuleCategory.CORE);
        ModuleRegistry.register(this);

    }

    public EnumTiers getTier() {
        return tier;
    }

}
