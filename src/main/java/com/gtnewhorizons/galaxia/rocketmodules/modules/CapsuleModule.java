package com.gtnewhorizons.galaxia.rocketmodules.modules;

import com.gtnewhorizons.galaxia.rocketmodules.RocketModule;

public class CapsuleModule extends RocketModule {

    public CapsuleModule() {
        super(1, "Capsule", 2.5, 3.0, 450.0, "capsule_3x2.5x3");
    }

    @Override
    public int getPassengerCapacity() {
        return 1;
    }

    @Override
    public double getSitOffset() {
        return -1.75;
    }
}
