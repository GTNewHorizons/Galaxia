package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

public final class LayoutCacheBundle {

    private final @Nullable StationLayout layout;

    private final Map<FacilityModuleKind, Integer> duplicateCounts = new EnumMap<>(FacilityModuleKind.class);

    private final Map<FacilityModuleKind, List<CapacityCluster>> capacityClusters = new EnumMap<>(
        FacilityModuleKind.class);
    private boolean capacityClustersDirty = true;

    public LayoutCacheBundle(@Nullable StationLayout layout) {
        this.layout = layout;
    }

    public static EnumSet<CacheKind> affectedBy(MutationKind mutation, FacilityModuleKind kind) {
        EnumSet<CacheKind> result = EnumSet.noneOf(CacheKind.class);
        switch (mutation) {
            case PLACE, DECONSTRUCT -> {
                result.add(CacheKind.DUPLICATE_COUNTS);
                if (kind.isCapacityModule()) {
                    result.add(CacheKind.CAPACITY_CLUSTERS);
                }
            }
            // T3.4: SET_TIER invalidates CAPACITY_CLUSTERS for capacity modules
            case SET_TIER -> {
                if (kind.isCapacityModule()) {
                    result.add(CacheKind.CAPACITY_CLUSTERS);
                }
            }
            // TODO: To be implemented in T7.4
            case SET_PARALLEL -> {}
            // TODO: To be implemented in T7.4
            case SET_ENABLED -> {}
        }
        return result;
    }

    public void applyMutation(MutationKind mutation, FacilityModuleKind kind) {
        invalidate(affectedBy(mutation, kind), mutation, kind);
    }

    public int duplicateCount(FacilityModuleKind kind) {
        return duplicateCounts.getOrDefault(kind, 0);
    }

    /**
     * Returns the cached capacity clusters for the given kind, rebuilding all capacity-kind
     * clusters from the layout if the cache is dirty. Returns an empty list for non-capacity kinds.
     */
    public List<CapacityCluster> getCapacityClusters(FacilityModuleKind kind) {
        if (!kind.isCapacityModule()) {
            return Collections.emptyList();
        }
        if (capacityClustersDirty) {
            rebuildCapacityClusters();
        }
        return capacityClusters.getOrDefault(kind, Collections.emptyList());
    }

    private void rebuildCapacityClusters() {
        capacityClusters.clear();
        if (layout == null) {
            capacityClustersDirty = false;
            return;
        }
        for (FacilityModuleKind k : FacilityModuleKind.values()) {
            if (k.isCapacityModule()) {
                capacityClusters.put(k, CapacityClusterBuilder.build(layout, k));
            }
        }
        capacityClustersDirty = false;
    }

    private void invalidate(EnumSet<CacheKind> caches, MutationKind mutation, FacilityModuleKind kind) {
        if (caches.contains(CacheKind.DUPLICATE_COUNTS)) {
            switch (mutation) {
                case PLACE -> duplicateCounts.merge(kind, 1, Integer::sum);
                case DECONSTRUCT -> duplicateCounts.computeIfPresent(kind, (k, v) -> Math.max(0, v - 1));
                // T3.4: SET_TIER does not affect DUPLICATE_COUNTS
                case SET_TIER -> {}
                // TODO: To be implemented in T7.4
                case SET_PARALLEL -> {}
                // TODO: To be implemented in T7.4
                case SET_ENABLED -> {}
            }
        }
        if (caches.contains(CacheKind.CAPACITY_CLUSTERS)) {
            capacityClustersDirty = true;
        }
    }
}
