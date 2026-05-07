package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import javax.annotation.Nonnull;

public record ModuleOperationPlan(ModuleOperationTargetSpec targetSpec, int buildTicks, int completionRefundPercent,
    boolean reserveItems, boolean voidCompletionRefund) {

    public ModuleOperationPlan(@Nonnull ModuleOperationTargetSpec targetSpec, int buildTicks,
        int completionRefundPercent, boolean reserveItems) {
        this(targetSpec, buildTicks, completionRefundPercent, reserveItems, false);
    }

    public ModuleOperationPlan {
        if (targetSpec == null) {
            throw new IllegalArgumentException("targetSpec must not be null");
        }
        if (buildTicks < 0) {
            throw new IllegalArgumentException("buildTicks must be >= 0: " + buildTicks);
        }
        if (completionRefundPercent < 0 || completionRefundPercent > 100) {
            throw new IllegalArgumentException(
                "completionRefundPercent must be within [0,100]: " + completionRefundPercent);
        }
    }
}
