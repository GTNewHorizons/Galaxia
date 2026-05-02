package com.gtnewhorizons.galaxia.registry.outpost.module;

import com.gtnewhorizons.galaxia.registry.interfaces.ICapacityModule;

public class ModuleTank implements ModuleComponent, ICapacityModule, IParallelModule {

    private byte parallel = 1;

    @Override
    public long baseCapacityForTier(ModuleTier tier) {
        return switch (tier) {
            case HV -> 16_000L;
            case EV -> 64_000L;
            case IV -> 256_000L;
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
