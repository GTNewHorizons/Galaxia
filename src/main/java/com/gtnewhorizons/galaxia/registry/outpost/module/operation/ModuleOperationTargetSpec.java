package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

public record ModuleOperationTargetSpec(ModuleOperationKind operationKind, FacilityModuleKind sourceModuleKind,
    ModuleTier sourceTier, String sourceVariantKey, FacilityModuleKind targetModuleKind, ModuleTier targetTier,
    String targetVariantKey) {

    public ModuleOperationTargetSpec(ModuleOperationKind operationKind, FacilityModuleKind sourceModuleKind,
        ModuleTier sourceTier, FacilityModuleKind targetModuleKind, ModuleTier targetTier, String targetVariantKey) {
        this(operationKind, sourceModuleKind, sourceTier, null, targetModuleKind, targetTier, targetVariantKey);
    }

    public ModuleOperationTargetSpec {
        if (operationKind == null) {
            throw new IllegalArgumentException("operationKind must not be null");
        }
        if (sourceTier != null && sourceModuleKind == null) {
            throw new IllegalArgumentException("sourceModuleKind must not be null when sourceTier is set");
        }
        if (targetTier != null && targetModuleKind == null) {
            throw new IllegalArgumentException("targetModuleKind must not be null when targetTier is set");
        }
        if (sourceVariantKey != null && sourceVariantKey.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("sourceVariantKey must be null or non-blank");
        }
        if (targetVariantKey != null && targetVariantKey.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("targetVariantKey must be null or non-blank");
        }
    }
}
