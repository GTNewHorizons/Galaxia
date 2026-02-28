package com.gtnewhorizons.galaxia.rocketmodules.modules;

import com.gtnewhorizons.galaxia.rocketmodules.IStackableModule;
import com.gtnewhorizons.galaxia.rocketmodules.RocketModule;

public class FuelTankModule extends RocketModule implements IStackableModule {

    public FuelTankModule() {
        super(0, "Fuel Tank", 5.0, 3.0, 1200.0, "fuel_tank_3x5x3");
    }

    @Override
    public double getFuelCapacity() {
        return 8000.0;
    }

    @Override
    public int getMaxStackSize() {
        return 7;
    }
}
