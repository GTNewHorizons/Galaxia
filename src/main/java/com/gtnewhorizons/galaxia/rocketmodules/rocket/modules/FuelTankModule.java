package com.gtnewhorizons.galaxia.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.IStackableModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

public class FuelTankModule extends RocketModule implements IStackableModule {

    public FuelTankModule() {
        super(0, "Fuel Tank", 5.0, 3.0, 1200.0, "fuel_tank_3x5x3");
        this.setFuelCapacity(8000);
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
