package com.gtnewhorizons.galaxia.registry.outpost.module;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;

public class ModulePower implements ModuleComponent, IParallelModule {

    public static int EU_TICK = 2048;

    private byte parallel = 1;

    public static void doNothing(ModuleInstance instance, AutomatedFacility outpost) {}

    @Override
    public byte getParallel() {
        return parallel;
    }

    @Override
    public void setParallel(byte parallel) {
        this.parallel = parallel;
    }
}
