package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One all-or-nothing change to an automated facility's inventory.
 */
public record InventoryExchange(Map<ItemStackWrapper, Long> itemInputs, Map<FluidKey, Long> fluidInputs,
    Map<ItemStackWrapper, Long> itemOutputs, Map<FluidKey, Long> fluidOutputs) {

    public InventoryExchange {
        itemInputs = immutableAmounts(itemInputs, "item inputs");
        fluidInputs = immutableAmounts(fluidInputs, "fluid inputs");
        itemOutputs = immutableAmounts(itemOutputs, "item outputs");
        fluidOutputs = immutableAmounts(fluidOutputs, "fluid outputs");
    }

    boolean isEmpty() {
        return itemInputs.isEmpty() && fluidInputs.isEmpty() && itemOutputs.isEmpty() && fluidOutputs.isEmpty();
    }

    long inputAmount(InventoryKey resource) {
        return resource instanceof ItemStackWrapper item ? itemInputs.getOrDefault(item, 0L)
            : fluidInputs.getOrDefault((FluidKey) resource, 0L);
    }

    long outputAmount(InventoryKey resource) {
        return resource instanceof ItemStackWrapper item ? itemOutputs.getOrDefault(item, 0L)
            : fluidOutputs.getOrDefault((FluidKey) resource, 0L);
    }

    private static <K extends InventoryKey> Map<K, Long> immutableAmounts(Map<K, Long> amounts, String role) {
        if (amounts == null)
            throw new IllegalArgumentException("Facility inventory exchange " + role + " must not be null");
        Map<K, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<K, Long> entry : amounts.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Facility inventory exchange " + role + " must not contain nulls");
            }
            if (entry.getValue() < 0L) {
                throw new IllegalArgumentException("Facility inventory exchange " + role + " must be non-negative");
            }
            if (entry.getValue() > 0L) copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
