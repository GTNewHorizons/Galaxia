package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.EnumModuleCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.IStackableModule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketModule;

public class EngineModule extends RocketModule implements IStackableModule {

    private double thrust;

    public EngineModule(int id, String name, double height, double width, double weight, String modelName,
        double thrust) {
        super(id, name, height, width, weight, modelName);
        this.thrust = thrust;
        setCategory(EnumModuleCategory.ENGINE);
    }

    public double getThrust() {
        return thrust;
    }

    @Override
    public int getMaxStackSize() {
        return 7;
    }
}
