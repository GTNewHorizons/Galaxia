package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

public interface ModuleOperationCostResolver {

    Map<ItemStack, Long> materialCost(ModuleOperationTargetSpec targetSpec);

    static ModuleOperationCostResolver fixed(Map<ItemStack, Long> cost) {
        Map<ItemStack, Long> fixedCost = sanitizeCost(cost);
        return targetSpec -> fixedCost;
    }

    static Map<ItemStack, Long> sanitizeCost(Map<ItemStack, Long> rawCost) {
        if (rawCost == null) {
            throw new IllegalArgumentException("ModuleOperationCostResolver: cost must not be null");
        }
        if (rawCost.isEmpty()) return Map.of();
        Map<ItemStack, Long> sanitized = new LinkedHashMap<>();
        for (Map.Entry<ItemStack, Long> entry : rawCost.entrySet()) {
            ItemStack stack = entry.getKey();
            Long amount = entry.getValue();
            if (stack == null) {
                throw new IllegalArgumentException("ModuleOperationCostResolver: cost contains null item stack");
            }
            if (amount == null || amount <= 0L) {
                throw new IllegalArgumentException(
                    "ModuleOperationCostResolver: cost amount must be > 0 for " + stack + ", got " + amount);
            }
            sanitized.put(stack.copy(), amount);
        }
        return Collections.unmodifiableMap(sanitized);
    }
}
