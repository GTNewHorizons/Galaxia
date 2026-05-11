package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

public record ModuleTierOperation(ModuleTier targetTier) implements IModuleOperation {

    public ModuleTierOperation {
        if (targetTier == null) {
            throw new IllegalArgumentException("targetTier must not be null");
        }
    }
}
