package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.EnumModuleCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.IStackableModule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketModule;

public class FuelTankModule extends RocketModule implements IStackableModule {

    private double capacity;

    public FuelTankModule(int id, String name, double height, double width, double weight, String modelName,
        double capacity) {
        super(id, name, height, width, weight, modelName);
        this.capacity = capacity;
        setCategory(EnumModuleCategory.FUEL_TANK);
    }

    public double getFuelCapacity() {
        return this.capacity;
    }

    @Override
    public int getMaxStackSize() {
        return 7;
    }
}
