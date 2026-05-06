package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import java.util.Map;

import net.minecraft.item.ItemStack;

public record ModuleOperationDefinition(ModuleOperationKind operationKind, int buildTicks, int completionRefundPercent,
    ModuleOperationCostResolver costResolver) {

    public ModuleOperationDefinition {
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
        if (costResolver == null) {
            throw new IllegalArgumentException("ModuleOperationDefinition: costResolver must not be null");
        }
    }

    public ModuleOperationPlan createPlan(ModuleOperationTargetSpec targetSpec, boolean reserveItems) {
        validateTarget(targetSpec);
        return new ModuleOperationPlan(targetSpec, buildTicks, completionRefundPercent, reserveItems);
    }

    public Map<ItemStack, Long> materialCost(ModuleOperationTargetSpec targetSpec) {
        validateTarget(targetSpec);
        return ModuleOperationCostResolver.sanitizeCost(costResolver.materialCost(targetSpec));
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
}
