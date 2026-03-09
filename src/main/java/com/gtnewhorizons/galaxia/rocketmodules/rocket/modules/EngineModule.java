package com.gtnewhorizons.galaxia.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.EnumModuleCategory;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.IStackableModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

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
