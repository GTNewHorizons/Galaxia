package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

public interface IModuleOperation {

    IModuleOperation DECONSTRUCTION = new IModuleOperation() {};

    default @Nullable ModuleTier targetTier() {
        return null;
    }
}
