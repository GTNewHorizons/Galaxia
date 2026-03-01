package com.gtnewhorizons.galaxia.rocketmodules.modules;

import com.gtnewhorizons.galaxia.rocketmodules.IStackableModule;
import com.gtnewhorizons.galaxia.rocketmodules.RocketModule;

public class EngineModule extends RocketModule implements IStackableModule {

    public EngineModule() {
        super(3, "Engine", 0.5, 3.0, 250.0, "engine_3x1x3");
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
