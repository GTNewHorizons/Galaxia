package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One all-or-nothing change to an automated facility's inventory.
 */
public record InventoryExchange(Map<InventoryKey, Long> inputs, Map<InventoryKey, Long> outputs) {

    public InventoryExchange {
        inputs = immutableAmounts(inputs, "inputs");
        outputs = immutableAmounts(outputs, "outputs");
    }

    private static Map<InventoryKey, Long> immutableAmounts(Map<? extends InventoryKey, Long> amounts, String role) {
        if (amounts == null)
            throw new IllegalArgumentException("Facility inventory exchange " + role + " must not be null");
        Map<InventoryKey, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<? extends InventoryKey, Long> entry : amounts.entrySet()) {
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
