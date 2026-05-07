package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

public final class ModuleOperationDefinition {

    private final ModuleOperationKind operationKind;
    private final int buildTicks;
    private final int completionRefundPercent;
    private final Map<ItemStack, Long> materialCost;

    public ModuleOperationDefinition(@Nonnull ModuleOperationKind operationKind, int buildTicks,
        int completionRefundPercent, @Nonnull Map<ItemStack, Long> materialCost) {
        this.operationKind = operationKind;
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
        if (!operationKind.buildPhaseRequired() && buildTicks < 0) {
            throw new IllegalArgumentException(
                "ModuleOperationDefinition: buildTicks must be >= 0 for " + operationKind + ", got " + buildTicks);
        }
        if (completionRefundPercent < 0 || completionRefundPercent > 100) {
            throw new IllegalArgumentException(
                "ModuleOperationDefinition: completionRefundPercent must be within [0,100], got "
                    + completionRefundPercent);
        }
    }

    public ModuleOperationKind operationKind() {
        return operationKind;
    }

    public int buildTicks() {
        return buildTicks;
    }

    public int completionRefundPercent() {
        return completionRefundPercent;
    }

    public ModuleOperationPlan createPlan(@Nonnull ModuleOperationTargetSpec targetSpec, boolean reserveItems) {
        validateTarget(targetSpec);
        return new ModuleOperationPlan(targetSpec, buildTicks, completionRefundPercent, reserveItems);
    }

    public Map<ItemStack, Long> materialCost(@Nonnull ModuleOperationTargetSpec targetSpec) {
        validateTarget(targetSpec);
        return materialCost;
    }

    private void validateTarget(ModuleOperationTargetSpec targetSpec) {
        if (targetSpec == null) {
            throw new IllegalArgumentException("ModuleOperationDefinition: targetSpec must not be null");
        }
        if (targetSpec.operationKind() != operationKind) {
            throw new IllegalArgumentException(
                "ModuleOperationDefinition: target operation kind " + targetSpec.operationKind()
                    + " does not match definition kind "
                    + operationKind);
        }
    }

    private static Map<ItemStack, Long> sanitizeCost(Map<ItemStack, Long> rawCost) {
        if (rawCost == null) {
            throw new IllegalArgumentException("ModuleOperationDefinition: cost must not be null");
        }
        if (rawCost.isEmpty()) return Map.of();
        Map<ItemStack, Long> sanitized = new LinkedHashMap<>();
        for (Map.Entry<ItemStack, Long> entry : rawCost.entrySet()) {
            ItemStack stack = entry.getKey();
            Long amount = entry.getValue();
            if (stack == null) {
                throw new IllegalArgumentException("ModuleOperationDefinition: cost contains null item stack");
            }
            if (amount == null || amount <= 0L) {
                throw new IllegalArgumentException(
                    "ModuleOperationDefinition: cost amount must be > 0 for " + stack + ", got " + amount);
            }
            sanitized.put(stack.copy(), amount);
        }
        return Collections.unmodifiableMap(sanitized);
    }
}
