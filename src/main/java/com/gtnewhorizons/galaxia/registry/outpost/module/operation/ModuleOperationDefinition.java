package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

public final class ModuleOperationDefinition {

    private final ModuleOperationKind operationKind;
    private final FacilityModuleKind sourceModuleKind;
    private final ModuleTier sourceTier;
    private final String sourceVariantKey;
    private final FacilityModuleKind targetModuleKind;
    private final ModuleTier targetTier;
    private final String targetVariantKey;
    private final String sourceFocusTierKey;
    private final String sourceFocusOreKey;
    private final String targetFocusTierKey;
    private final String targetFocusOreKey;
    private final int buildTicks;
    private final int completionRefundPercent;
    private final Map<ItemStackWrapper, Long> materialCost;

    public ModuleOperationDefinition(@Nonnull ModuleOperationKind operationKind, int buildTicks,
        int completionRefundPercent, @Nonnull Map<ItemStackWrapper, Long> materialCost) {
        this(
            operationKind,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            buildTicks,
            completionRefundPercent,
            materialCost);
    }

    public ModuleOperationDefinition(@Nonnull ModuleOperationKind operationKind, FacilityModuleKind sourceModuleKind,
        ModuleTier sourceTier, String sourceVariantKey, FacilityModuleKind targetModuleKind, ModuleTier targetTier,
        String targetVariantKey, String sourceFocusTierKey, String sourceFocusOreKey, String targetFocusTierKey,
        String targetFocusOreKey, int buildTicks, int completionRefundPercent,
        @Nonnull Map<ItemStackWrapper, Long> materialCost) {
        this.operationKind = operationKind;
        this.sourceModuleKind = sourceModuleKind;
        this.sourceTier = sourceTier;
        this.sourceVariantKey = sourceVariantKey;
        this.targetModuleKind = targetModuleKind;
        this.targetTier = targetTier;
        this.targetVariantKey = targetVariantKey;
        this.sourceFocusTierKey = sourceFocusTierKey;
        this.sourceFocusOreKey = sourceFocusOreKey;
        this.targetFocusTierKey = targetFocusTierKey;
        this.targetFocusOreKey = targetFocusOreKey;
        this.buildTicks = buildTicks;
        this.completionRefundPercent = completionRefundPercent;
        this.materialCost = sanitizeCost(materialCost);
        if (operationKind == null) {
            throw new IllegalArgumentException("ModuleOperationDefinition: operationKind must not be null");
        }
        if (operationKind.buildPhaseRequired() && buildTicks <= 0) {
            throw new IllegalArgumentException(
                "ModuleOperationDefinition: buildTicks must be > 0 for " + operationKind + ", got " + buildTicks);
        }
        if (!operationKind.buildPhaseRequired() && buildTicks != 0) {
            throw new IllegalArgumentException(
                "ModuleOperationDefinition: buildTicks must be 0 for " + operationKind + ", got " + buildTicks);
        }
        if (completionRefundPercent < 0 || completionRefundPercent > 100) {
            throw new IllegalArgumentException(
                "ModuleOperationDefinition: completionRefundPercent must be within [0,100], got "
                    + completionRefundPercent);
        }
        validateSpec();
    }

    public ModuleOperationKind operationKind() {
        return operationKind;
    }

    public FacilityModuleKind sourceModuleKind() {
        return sourceModuleKind;
    }

    public ModuleTier sourceTier() {
        return sourceTier;
    }

    public String sourceVariantKey() {
        return sourceVariantKey;
    }

    public FacilityModuleKind targetModuleKind() {
        return targetModuleKind;
    }

    public ModuleTier targetTier() {
        return targetTier;
    }

    public String targetVariantKey() {
        return targetVariantKey;
    }

    public String sourceFocusTierKey() {
        return sourceFocusTierKey;
    }

    public String sourceFocusOreKey() {
        return sourceFocusOreKey;
    }

    public String targetFocusTierKey() {
        return targetFocusTierKey;
    }

    public String targetFocusOreKey() {
        return targetFocusOreKey;
    }

    public int buildTicks() {
        return buildTicks;
    }

    public int completionRefundPercent() {
        return completionRefundPercent;
    }

    public ModuleOperationPlan createPlan(boolean reserveItems) {
        return new ModuleOperationPlan(this, reserveItems);
    }

    public Map<ItemStackWrapper, Long> materialCost() {
        return materialCost;
    }

    public ModuleOperationDefinition withTarget(FacilityModuleKind sourceModuleKind, ModuleTier sourceTier,
        FacilityModuleKind targetModuleKind, ModuleTier targetTier, String targetVariantKey) {
        return withTarget(sourceModuleKind, sourceTier, null, targetModuleKind, targetTier, targetVariantKey);
    }

    public ModuleOperationDefinition withTarget(FacilityModuleKind sourceModuleKind, ModuleTier sourceTier,
        String sourceVariantKey, FacilityModuleKind targetModuleKind, ModuleTier targetTier, String targetVariantKey) {
        return withTarget(
            sourceModuleKind,
            sourceTier,
            sourceVariantKey,
            targetModuleKind,
            targetTier,
            targetVariantKey,
            null,
            null,
            null,
            null);
    }

    public ModuleOperationDefinition withTarget(FacilityModuleKind sourceModuleKind, ModuleTier sourceTier,
        String sourceVariantKey, FacilityModuleKind targetModuleKind, ModuleTier targetTier, String targetVariantKey,
        String sourceFocusTierKey, String sourceFocusOreKey, String targetFocusTierKey, String targetFocusOreKey) {
        return new ModuleOperationDefinition(
            operationKind,
            sourceModuleKind,
            sourceTier,
            sourceVariantKey,
            targetModuleKind,
            targetTier,
            targetVariantKey,
            sourceFocusTierKey,
            sourceFocusOreKey,
            targetFocusTierKey,
            targetFocusOreKey,
            buildTicks,
            completionRefundPercent,
            materialCost);
    }

    private void validateSpec() {
        if (sourceTier != null && sourceModuleKind == null) {
            throw new IllegalArgumentException(
                "ModuleOperationDefinition: sourceModuleKind must be set with sourceTier");
        }
        if (targetTier != null && targetModuleKind == null) {
            throw new IllegalArgumentException(
                "ModuleOperationDefinition: targetModuleKind must be set with targetTier");
        }
        requireOptionalNonBlank(sourceVariantKey, "sourceVariantKey");
        requireOptionalNonBlank(targetVariantKey, "targetVariantKey");
        requireOptionalNonBlank(sourceFocusTierKey, "sourceFocusTierKey");
        requireOptionalNonBlank(sourceFocusOreKey, "sourceFocusOreKey");
        requireOptionalNonBlank(targetFocusTierKey, "targetFocusTierKey");
        requireOptionalNonBlank(targetFocusOreKey, "targetFocusOreKey");
    }

    private static void requireOptionalNonBlank(String value, String fieldName) {
        if (value != null && value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(
                "ModuleOperationDefinition: " + fieldName + " must be null or non-blank");
        }
    }

    private static Map<ItemStackWrapper, Long> sanitizeCost(Map<ItemStackWrapper, Long> rawCost) {
        if (rawCost == null) {
            throw new IllegalArgumentException("ModuleOperationDefinition: cost must not be null");
        }
        if (rawCost.isEmpty()) return Map.of();
        Map<ItemStackWrapper, Long> sanitized = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : rawCost.entrySet()) {
            ItemStackWrapper item = entry.getKey();
            Long amount = entry.getValue();
            if (item == null) {
                throw new IllegalArgumentException("ModuleOperationDefinition: cost contains null item");
            }
            if (amount == null || amount <= 0L) {
                throw new IllegalArgumentException(
                    "ModuleOperationDefinition: cost amount must be > 0 for " + item + ", got " + amount);
            }
            sanitized.put(item, amount);
        }
        return Collections.unmodifiableMap(sanitized);
    }
}
