package com.gtnewhorizons.galaxia.registry.outpost.module.types;

import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;

public class ModuleStorage implements IModuleComponent, IParallelModule {

    private byte parallel = 1;

    @Override
    public byte getParallel() {
        return parallel;
    }

    @Override
    public void setParallel(byte parallel) {
        this.parallel = parallel;
    }
}
