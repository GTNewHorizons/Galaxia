package com.gtnewhorizons.galaxia.registry.outpost.module.types;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.TieredModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public class ModulePower extends TieredModuleComponent implements IParallelModule {

    public static int EU_TICK = 2048;

    private byte parallel = 1;

    public static void doNothing(ModuleInstance instance, CelestialAsset outpost) {}

    @Override
    public byte getParallel() {
        return parallel;
    }

    @Override
    public void setParallel(byte parallel) {
        this.parallel = parallel;
    }
}
