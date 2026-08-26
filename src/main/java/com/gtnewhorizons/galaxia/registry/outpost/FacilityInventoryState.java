package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class FacilityInventoryState {

    private final Map<ItemStackWrapper, Long> amounts = new LinkedHashMap<>();
    private final Map<FluidKey, Long> fluidAmounts = new LinkedHashMap<>();

    Map<ItemStackWrapper, Long> itemAmounts() {
        return amounts;
    }

    Map<FluidKey, Long> fluidAmounts() {
        return fluidAmounts;
    }

    Map<ItemStackWrapper, Long> itemSnapshot() {
        Map<ItemStackWrapper, Long> result = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> e : amounts.entrySet()) {
            result.put(e.getKey(), e.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    Map<String, Long> fluidSnapshot() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<FluidKey, Long> e : fluidAmounts.entrySet()) {
            result.put(
                e.getKey()
                    .fluid()
                    .getName(),
                e.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    void loadFromSnapshot(Map<ItemStackWrapper, Long> snapshot) {
        amounts.clear();
        for (Map.Entry<ItemStackWrapper, Long> e : snapshot.entrySet()) {
            if (e.getValue() > 0) {
                amounts.put(e.getKey(), e.getValue());
            }
        }
    }

    void loadFluidSnapshot(Map<String, Long> snapshot) {
        fluidAmounts.clear();
        for (Map.Entry<String, Long> e : snapshot.entrySet()) {
            if (e.getKey() == null || e.getKey()
                .isEmpty() || e.getValue() <= 0) continue;
            FluidKey key = FluidKey.fromName(e.getKey());
            if (key != null) fluidAmounts.put(key, e.getValue());
        }
    }

    void clearAmounts() {
        amounts.clear();
        fluidAmounts.clear();
    }
}
