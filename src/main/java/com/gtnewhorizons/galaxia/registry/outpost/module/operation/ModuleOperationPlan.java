package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

public record ModuleOperationPlan(ModuleOperationDefinition definition, boolean reserveItems,
    boolean voidCompletionRefund) {

    public ModuleOperationPlan(@Nonnull ModuleOperationDefinition definition, boolean reserveItems) {
        this(definition, reserveItems, false);
    }

    public ModuleOperationPlan {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
    }

    public ModuleOperationKind operationKind() {
        return definition.operationKind();
    }

    public FacilityModuleKind sourceModuleKind() {
        return definition.sourceModuleKind();
    }

    public ModuleTier sourceTier() {
        return definition.sourceTier();
    }

    public String sourceVariantKey() {
        return definition.sourceVariantKey();
    }

    public FacilityModuleKind targetModuleKind() {
        return definition.targetModuleKind();
    }

    public ModuleTier targetTier() {
        return definition.targetTier();
    }

    public String targetVariantKey() {
        return definition.targetVariantKey();
    }

    public String sourceFocusTierKey() {
        return definition.sourceFocusTierKey();
    }

    public String sourceFocusOreKey() {
        return definition.sourceFocusOreKey();
    }

    public String targetFocusTierKey() {
        return definition.targetFocusTierKey();
    }

    public String targetFocusOreKey() {
        return definition.targetFocusOreKey();
    }

    public int buildTicks() {
        return definition.buildTicks();
    }

    public int completionRefundPercent() {
        return definition.completionRefundPercent();
    }
}
