package com.gtnewhorizons.galaxia.registry.outpost.module.types;

import com.gtnewhorizons.galaxia.registry.interfaces.TieredModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;

public class ModuleTank extends TieredModuleComponent implements IParallelModule {

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
