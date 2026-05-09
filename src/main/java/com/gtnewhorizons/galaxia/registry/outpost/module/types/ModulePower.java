package com.gtnewhorizons.galaxia.registry.outpost.module.types;

import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public class ModulePower implements IModuleComponent, IParallelModule {

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
