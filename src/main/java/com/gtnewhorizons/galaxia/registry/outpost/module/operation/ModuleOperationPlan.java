package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

public record ModuleOperationPlan(@Nonnull IModuleOperation spec, int buildTicks,
    @Nonnull Map<ItemStackWrapper, Long> materialCost, boolean reserveItems, boolean voidCompletionRefund) {

    public ModuleOperationPlan(@Nonnull IModuleOperation spec, int buildTicks,
        @Nonnull Map<ItemStackWrapper, Long> materialCost, boolean reserveItems) {
        this(spec, buildTicks, materialCost, reserveItems, false);
    }

    public ModuleOperationPlan {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (buildTicks <= 0) {
            throw new IllegalArgumentException("buildTicks must be > 0, got " + buildTicks);
        }
        materialCost = sanitizeCost(materialCost);
    }

    private static Map<ItemStackWrapper, Long> sanitizeCost(Map<ItemStackWrapper, Long> rawCost) {
        if (rawCost == null) {
            throw new IllegalArgumentException("materialCost must not be null");
        }
        if (rawCost.isEmpty()) return Map.of();
        Map<ItemStackWrapper, Long> sanitized = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : rawCost.entrySet()) {
            ItemStackWrapper item = entry.getKey();
            Long amount = entry.getValue();
            if (item == null) {
                throw new IllegalArgumentException("materialCost contains null item");
            }
            if (amount == null || amount <= 0L) {
                throw new IllegalArgumentException("materialCost amount must be > 0 for " + item + ", got " + amount);
            }
            sanitized.put(item, amount);
        }
        return Collections.unmodifiableMap(sanitized);
    }
}
