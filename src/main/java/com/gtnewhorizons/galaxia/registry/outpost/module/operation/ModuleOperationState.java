package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

public final class ModuleOperationState {

    private ModuleOperationPlan plan;
    private ModuleOperationPhase phase;
    private int elapsedBuildTicks;
    private Map<String, Long> depositedResources;
    private Map<String, Long> refundBuffer;

    private ModuleOperationState(@Nonnull ModuleOperationPlan plan, @Nonnull ModuleOperationPhase phase,
        int elapsedBuildTicks, @Nonnull Map<String, Long> depositedResources, @Nonnull Map<String, Long> refundBuffer) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        if (phase == null) {
            throw new IllegalArgumentException("phase must not be null");
        }
        if (elapsedBuildTicks < 0) {
            throw new IllegalArgumentException("elapsedBuildTicks must be >= 0: " + elapsedBuildTicks);
        }
        this.plan = plan;
        this.phase = phase;
        this.elapsedBuildTicks = elapsedBuildTicks;
        this.depositedResources = sanitizeItemAmounts(depositedResources, "depositedResources");
        this.refundBuffer = sanitizeItemAmounts(refundBuffer, "refundBuffer");
        validatePhaseDataConsistency();
    }

    public static ModuleOperationState waiting(@Nonnull ModuleOperationPlan plan) {
        return new ModuleOperationState(plan, ModuleOperationPhase.WAITING_FOR_MATERIALS, 0, Map.of(), Map.of());
    }

    public static ModuleOperationState restore(@Nonnull ModuleOperationPlan plan, @Nonnull ModuleOperationPhase phase,
        int elapsedBuildTicks, @Nonnull Map<String, Long> depositedResources, @Nonnull Map<String, Long> refundBuffer) {
        return new ModuleOperationState(plan, phase, elapsedBuildTicks, depositedResources, refundBuffer);
    }

    public ModuleOperationPlan plan() {
        return plan;
    }

    public ModuleOperationPhase phase() {
        return phase;
    }

    public int elapsedBuildTicks() {
        return elapsedBuildTicks;
    }

    public Map<String, Long> depositedResources() {
        return depositedResources;
    }

    public Map<String, Long> refundBuffer() {
        return refundBuffer;
    }

    public boolean reserveItems() {
        return plan.reserveItems();
    }

    public ModuleOperationState withDepositedResources(@Nonnull Map<String, Long> updatedDeposits) {
        this.depositedResources = sanitizeItemAmounts(updatedDeposits, "depositedResources");
        validatePhaseDataConsistency();
        return this;
    }

    public ModuleOperationState withRefundBuffer(@Nonnull Map<String, Long> updatedRefundBuffer) {
        this.refundBuffer = sanitizeItemAmounts(updatedRefundBuffer, "refundBuffer");
        validatePhaseDataConsistency();
        return this;
    }

    public ModuleOperationState beginBuilding() {
        if (phase != ModuleOperationPhase.WAITING_FOR_MATERIALS) {
            throw new IllegalStateException("beginBuilding requires WAITING_FOR_MATERIALS phase, got " + phase);
        }
        this.phase = ModuleOperationPhase.BUILDING;
        this.elapsedBuildTicks = 0;
        validatePhaseDataConsistency();
        return this;
    }

    public ModuleOperationState refundAfterCompletion(@Nonnull Map<String, Long> completionRefund) {
        if (phase != ModuleOperationPhase.COMPLETE) {
            throw new IllegalStateException("refundAfterCompletion requires COMPLETE phase, got " + phase);
        }
        this.phase = ModuleOperationPhase.REFUNDING;
        this.depositedResources = Map.of();
        this.refundBuffer = sanitizeItemAmounts(completionRefund, "refundBuffer");
        validatePhaseDataConsistency();
        return this;
    }

    public ModuleOperationState tickBuilding() {
        if (phase != ModuleOperationPhase.BUILDING) {
            throw new IllegalStateException("tickBuilding requires BUILDING phase, got " + phase);
        }
        int nextElapsed = elapsedBuildTicks + 1;
        this.elapsedBuildTicks = nextElapsed;
        if (nextElapsed >= plan.buildTicks()) {
            this.phase = ModuleOperationPhase.COMPLETE;
            this.refundBuffer = Map.of();
        }
        validatePhaseDataConsistency();
        return this;
    }

    public ModuleOperationState cancel() {
        if (phase != ModuleOperationPhase.WAITING_FOR_MATERIALS && phase != ModuleOperationPhase.BUILDING) {
            throw new IllegalStateException(
                "cancel is only allowed from WAITING_FOR_MATERIALS or BUILDING, got " + phase);
        }
        if (depositedResources.isEmpty()) {
            this.phase = ModuleOperationPhase.CANCELLED;
            this.refundBuffer = Map.of();
            validatePhaseDataConsistency();
            return this;
        }
        this.phase = ModuleOperationPhase.REFUNDING;
        this.refundBuffer = depositedResources;
        validatePhaseDataConsistency();
        return this;
    }

    public ModuleOperationState finishRefunding() {
        if (phase != ModuleOperationPhase.REFUNDING) {
            throw new IllegalStateException("finishRefunding requires REFUNDING phase, got " + phase);
        }
        this.phase = ModuleOperationPhase.CANCELLED;
        this.depositedResources = Map.of();
        this.refundBuffer = Map.of();
        validatePhaseDataConsistency();
        return this;
    }

    private void validatePhaseDataConsistency() {
        if (phase == ModuleOperationPhase.WAITING_FOR_MATERIALS && elapsedBuildTicks != 0) {
            throw new IllegalStateException(
                "WAITING_FOR_MATERIALS phase requires elapsedBuildTicks == 0, got " + elapsedBuildTicks);
        }

        if (phase == ModuleOperationPhase.BUILDING) {
            if (elapsedBuildTicks >= plan.buildTicks()) {
                throw new IllegalStateException(
                    "BUILDING phase requires elapsedBuildTicks < buildTicks (" + plan.buildTicks()
                        + "), got "
                        + elapsedBuildTicks);
            }
        }

        if (phase == ModuleOperationPhase.COMPLETE && elapsedBuildTicks < plan.buildTicks()) {
            throw new IllegalStateException(
                "COMPLETE phase requires elapsedBuildTicks >= buildTicks (" + plan.buildTicks()
                    + "), got "
                    + elapsedBuildTicks);
        }

        if (phase == ModuleOperationPhase.REFUNDING && refundBuffer.isEmpty()) {
            throw new IllegalStateException("REFUNDING phase requires non-empty refundBuffer");
        }
        if (phase != ModuleOperationPhase.REFUNDING && !refundBuffer.isEmpty()) {
            throw new IllegalStateException("refundBuffer must be empty unless phase is REFUNDING, phase=" + phase);
        }
    }

    private static Map<String, Long> sanitizeItemAmounts(Map<String, Long> raw, String fieldName) {
        if (raw == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : raw.entrySet()) {
            String itemKey = entry.getKey();
            Long amount = entry.getValue();
            if (itemKey == null || itemKey.trim()
                .isEmpty()) {
                throw new IllegalArgumentException(fieldName + " contains null/blank item key");
            }
            if (amount == null || amount <= 0) {
                throw new IllegalArgumentException(
                    fieldName + " amount must be > 0 for item '" + itemKey + "', got " + amount);
            }
            sanitized.put(itemKey, amount);
        }
        return Collections.unmodifiableMap(sanitized);
    }
}
