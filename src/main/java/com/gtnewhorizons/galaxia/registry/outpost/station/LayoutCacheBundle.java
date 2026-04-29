package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

public final class LayoutCacheBundle {

    private final Map<FacilityModuleKind, Integer> duplicateCounts = new EnumMap<>(FacilityModuleKind.class);

    public static EnumSet<CacheKind> affectedBy(MutationKind mutation, FacilityModuleKind kind) {
        EnumSet<CacheKind> result = EnumSet.noneOf(CacheKind.class);
        switch (mutation) {
            case PLACE -> result.add(CacheKind.DUPLICATE_COUNTS);
            case DECONSTRUCT -> result.add(CacheKind.DUPLICATE_COUNTS);
        }
        return result;
    }

    public void applyMutation(MutationKind mutation, FacilityModuleKind kind) {
        invalidate(affectedBy(mutation, kind), mutation, kind);
    }

    public int duplicateCount(FacilityModuleKind kind) {
        return duplicateCounts.getOrDefault(kind, 0);
    }

    private void invalidate(EnumSet<CacheKind> caches, MutationKind mutation, FacilityModuleKind kind) {
        if (caches.contains(CacheKind.DUPLICATE_COUNTS)) {
            if (mutation == MutationKind.PLACE) {
                duplicateCounts.merge(kind, 1, Integer::sum);
            } else if (mutation == MutationKind.DECONSTRUCT) {
                duplicateCounts.computeIfPresent(kind, (k, v) -> Math.max(0, v - 1));
            }
        }
    }
}
