package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

public record ModuleOperationPlan(ModuleOperationTargetSpec targetSpec, int buildTicks, int completionRefundPercent,
    boolean reserveItems) {

    public ModuleOperationPlan {
        if (targetSpec == null) {
            throw new IllegalArgumentException("targetSpec must not be null");
        }
        if (targetSpec.operationKind()
            .buildPhaseRequired() && buildTicks <= 0) {
            throw new IllegalArgumentException(
                "buildTicks must be > 0 for operation kind " + targetSpec.operationKind() + ": " + buildTicks);
        }
        if (!targetSpec.operationKind()
            .buildPhaseRequired() && buildTicks < 0) {
            throw new IllegalArgumentException(
                "buildTicks must be >= 0 for operation kind " + targetSpec.operationKind() + ": " + buildTicks);
        }
        if (completionRefundPercent < 0 || completionRefundPercent > 100) {
            throw new IllegalArgumentException(
                "completionRefundPercent must be within [0,100]: " + completionRefundPercent);
        }
    }
}
