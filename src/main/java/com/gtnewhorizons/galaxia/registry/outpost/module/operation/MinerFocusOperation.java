package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

public record MinerFocusOperation(ModuleTier targetTier, String targetFocusTierKey, @Nullable String targetFocusOreKey)
    implements IModuleOperation {

    public MinerFocusOperation {
        if (targetFocusTierKey == null) {
            throw new IllegalArgumentException("targetFocusTierKey must not be null");
        }
    }
}
