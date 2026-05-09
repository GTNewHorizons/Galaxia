package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

public record HammerModuleOperation(ModuleTier targetTier, String targetVariantKey) implements IModuleOperation {

    public HammerModuleOperation {
        if (targetVariantKey == null || targetVariantKey.isBlank()) {
            throw new IllegalArgumentException("targetVariantKey must not be null or blank");
        }
    }
}
