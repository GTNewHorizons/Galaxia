package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public final class LayoutCacheBundle {

    private final @Nullable StationLayout layout;

    private final Map<FacilityModuleKind, Integer> duplicateCounts = new EnumMap<>(FacilityModuleKind.class);

    private final Map<FacilityModuleKind, List<CapacityCluster>> capacityClusters = new EnumMap<>(
        FacilityModuleKind.class);
    private boolean capacityClustersDirty = true;

    private Set<StationTileCoord> maintenanceCoverage;
    private boolean maintenanceCoverageDirty = true;

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
                if (hasAreaEffects(kind)) {
                    result.add(CacheKind.MAINTENANCE_COVERAGE);
                }
            }
            case SET_TIER -> {
                if (kind.isCapacityModule()) {
                    result.add(CacheKind.CAPACITY_CLUSTERS);
                }
            }
            case SET_ENABLED -> {
                if (hasAreaEffects(kind)) {
                    result.add(CacheKind.MAINTENANCE_COVERAGE);
                }
            }
            // TODO: To be implemented in T7.4
            case SET_PARALLEL -> {}
        }
        return result;
    }

    private static boolean hasAreaEffects(FacilityModuleKind kind) {
        FacilityModuleRegistry.Definition definition = FacilityModuleRegistry.get(kind);
        return definition != null && !definition.areaEffects()
            .isEmpty();
    }

    public void applyMutation(MutationKind mutation, FacilityModuleKind kind) {
        invalidate(affectedBy(mutation, kind), mutation, kind);
    }

    public void applyMutation(MutationKind mutation, FacilityModuleKind kind, ModuleInstance module) {
        invalidate(affectedBy(mutation, kind), mutation, kind);
    }

    public int duplicateCount(FacilityModuleKind kind) {
        return duplicateCounts.getOrDefault(kind, 0);
    }

    public List<CapacityCluster> getCapacityClusters(FacilityModuleKind kind) {
        if (!kind.isCapacityModule()) {
            return Collections.emptyList();
        }
        if (capacityClustersDirty) {
            rebuildCapacityClusters();
        }
        return capacityClusters.getOrDefault(kind, Collections.emptyList());
    }

    public List<CapacityCluster> getCapacityClustersExcluding(FacilityModuleKind kind,
        ModuleInstance.ID excludedModuleId) {
        if (layout == null) {
            return Collections.emptyList();
        }
        return CapacityClusterBuilder.buildExcluding(layout, kind, excludedModuleId);
    }

    private void rebuildCapacityClusters() {
        capacityClusters.clear();
        if (layout == null) {
            capacityClustersDirty = false;
            return;
        }
        for (FacilityModuleKind k : FacilityModuleKind.values()) {
            if (k.isCapacityModule()) {
                capacityClusters.put(k, new ArrayList<>(CapacityClusterBuilder.build(layout, k)));
            }
        }
        capacityClustersDirty = false;
    }

    // ── Maintenance coverage ──

    public Set<StationTileCoord> getMaintenanceCoverage() {
        if (maintenanceCoverageDirty) {
            rebuildMaintenanceCoverage();
        }
        return Collections.unmodifiableSet(maintenanceCoverage);
    }

    private void rebuildMaintenanceCoverage() {
        maintenanceCoverage = new HashSet<>();
        if (layout == null) {
            maintenanceCoverageDirty = false;
            return;
        }
        layout.forEachAnchor(
            (coord, module) -> {
                module.areaEffects()
                    .forEach(effect -> effect.collectAffectedTiles(module, maintenanceCoverage::add));
            });
        maintenanceCoverageDirty = false;
    }

    private void invalidate(EnumSet<CacheKind> caches, MutationKind mutation, FacilityModuleKind kind) {
        if (caches.contains(CacheKind.DUPLICATE_COUNTS)) {
            switch (mutation) {
                case PLACE -> duplicateCounts.merge(kind, 1, Integer::sum);
                case DECONSTRUCT -> duplicateCounts.computeIfPresent(kind, (k, v) -> Math.max(0, v - 1));
                case SET_TIER -> {}
                case SET_ENABLED -> {}
                // TODO: To be implemented in T7.4
                case SET_PARALLEL -> {}
            }
        }
        if (caches.contains(CacheKind.CAPACITY_CLUSTERS)) {
            capacityClustersDirty = true;
        }
        if (caches.contains(CacheKind.MAINTENANCE_COVERAGE)) {
            maintenanceCoverageDirty = true;
        }
    }
}
