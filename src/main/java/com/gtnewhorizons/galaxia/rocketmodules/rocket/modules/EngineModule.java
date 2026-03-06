package com.gtnewhorizons.galaxia.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.IStackableModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

public class EngineModule extends RocketModule implements IStackableModule {

    public EngineModule() {
        super(3, "Engine", 0.5, 3.0, 250.0, "engine_3x1x3");
        super.setThrust(10);
    }

    @Override
    public double getThrust() {
        return 6000.0;
    }

    @Override
    public int getMaxStackSize() {
        return 7;
    }
}
