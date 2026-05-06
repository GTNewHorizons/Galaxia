package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModuleOperationState {

    private final ModuleOperationPlan plan;
    private final ModuleOperationPhase phase;
    private final int elapsedBuildTicks;
    private final Map<String, Long> depositedResources;
    private final Map<String, Long> refundBuffer;

    private ModuleOperationState(ModuleOperationPlan plan, ModuleOperationPhase phase, int elapsedBuildTicks,
        Map<String, Long> depositedResources, Map<String, Long> refundBuffer) {
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

    public static ModuleOperationState waiting(ModuleOperationPlan plan) {
        return new ModuleOperationState(plan, ModuleOperationPhase.WAITING_FOR_MATERIALS, 0, Map.of(), Map.of());
    }

    public static ModuleOperationState restore(ModuleOperationPlan plan, ModuleOperationPhase phase,
        int elapsedBuildTicks, Map<String, Long> depositedResources, Map<String, Long> refundBuffer) {
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

    public ModuleOperationState withDepositedResources(Map<String, Long> updatedDeposits) {
        return new ModuleOperationState(plan, phase, elapsedBuildTicks, updatedDeposits, refundBuffer);
    }

    public ModuleOperationState beginBuilding() {
        if (phase != ModuleOperationPhase.WAITING_FOR_MATERIALS) {
            throw new IllegalStateException("beginBuilding requires WAITING_FOR_MATERIALS phase, got " + phase);
        }
        return new ModuleOperationState(plan, ModuleOperationPhase.BUILDING, 0, depositedResources, refundBuffer);
    }

    public ModuleOperationState refundAfterCompletion(Map<String, Long> completionRefund) {
        if (phase != ModuleOperationPhase.COMPLETE) {
            throw new IllegalStateException("refundAfterCompletion requires COMPLETE phase, got " + phase);
        }
        return new ModuleOperationState(
            plan,
            ModuleOperationPhase.REFUNDING,
            elapsedBuildTicks,
            Map.of(),
            completionRefund);
    }

    public ModuleOperationState tickBuilding() {
        if (phase != ModuleOperationPhase.BUILDING) {
            throw new IllegalStateException("tickBuilding requires BUILDING phase, got " + phase);
        }
        int nextElapsed = elapsedBuildTicks + 1;
        if (nextElapsed < plan.buildTicks()) {
            return new ModuleOperationState(
                plan,
                ModuleOperationPhase.BUILDING,
                nextElapsed,
                depositedResources,
                refundBuffer);
        }
        return new ModuleOperationState(plan, ModuleOperationPhase.COMPLETE, nextElapsed, depositedResources, Map.of());
    }

    public ModuleOperationState cancel() {
        if (phase != ModuleOperationPhase.WAITING_FOR_MATERIALS && phase != ModuleOperationPhase.BUILDING) {
            throw new IllegalStateException(
                "cancel is only allowed from WAITING_FOR_MATERIALS or BUILDING, got " + phase);
        }
        if (depositedResources.isEmpty()) {
            return new ModuleOperationState(
                plan,
                ModuleOperationPhase.CANCELLED,
                elapsedBuildTicks,
                depositedResources,
                Map.of());
        }
        return new ModuleOperationState(
            plan,
            ModuleOperationPhase.REFUNDING,
            elapsedBuildTicks,
            depositedResources,
            depositedResources);
    }

    public ModuleOperationState finishRefunding() {
        if (phase != ModuleOperationPhase.REFUNDING) {
            throw new IllegalStateException("finishRefunding requires REFUNDING phase, got " + phase);
        }
        return new ModuleOperationState(plan, ModuleOperationPhase.CANCELLED, elapsedBuildTicks, Map.of(), Map.of());
    }

    private void validatePhaseDataConsistency() {
        if (phase == ModuleOperationPhase.WAITING_FOR_MATERIALS && elapsedBuildTicks != 0) {
            throw new IllegalStateException(
                "WAITING_FOR_MATERIALS phase requires elapsedBuildTicks == 0, got " + elapsedBuildTicks);
        }

        if (phase == ModuleOperationPhase.BUILDING) {
            if (!plan.targetSpec()
                .operationKind()
                .buildPhaseRequired()) {
                throw new IllegalStateException(
                    "BUILDING phase is not valid for operation kind " + plan.targetSpec()
                        .operationKind());
            }
            if (elapsedBuildTicks >= plan.buildTicks()) {
                throw new IllegalStateException(
                    "BUILDING phase requires elapsedBuildTicks < buildTicks (" + plan.buildTicks()
                        + "), got "
                        + elapsedBuildTicks);
            }
        }

        if (phase == ModuleOperationPhase.COMPLETE && plan.targetSpec()
            .operationKind()
            .buildPhaseRequired() && elapsedBuildTicks < plan.buildTicks()) {
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
