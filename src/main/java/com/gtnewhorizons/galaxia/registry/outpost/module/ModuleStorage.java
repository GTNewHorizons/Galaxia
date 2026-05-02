package com.gtnewhorizons.galaxia.registry.outpost.module;

import com.gtnewhorizons.galaxia.registry.interfaces.ICapacityModule;

public class ModuleStorage implements ModuleComponent, ICapacityModule, IParallelModule {

    private byte parallel = 1;

    @Override
    public long baseCapacityForTier(ModuleTier tier) {
        return switch (tier) {
            case HV -> 1024L;
            case EV -> 4096L;
            case IV -> 16384L;
            default -> 0L;
        };
    }

    @Override
    public byte getParallel() {
        return parallel;
    }

    @Override
    public void setParallel(byte parallel) {
        this.parallel = parallel;
    }
}
