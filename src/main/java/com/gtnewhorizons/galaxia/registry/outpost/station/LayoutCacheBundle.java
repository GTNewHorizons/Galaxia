package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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
    private long layoutVersion = Long.MIN_VALUE;

    private final Map<FacilityModuleKind, List<CapacityCluster>> capacityClusters = new EnumMap<>(
        FacilityModuleKind.class);
    private boolean capacityClustersDirty = true;

    private Set<StationTileCoord> maintenanceCoverage;
    private boolean maintenanceCoverageDirty = true;

    public LayoutCacheBundle(@Nullable StationLayout layout) {
        this.layout = layout;
    }

    public void applyMutation(MutationKind mutation, FacilityModuleKind kind) {
        switch (mutation) {
            case PLACE, DECONSTRUCT -> {
                capacityClustersDirty |= kind.isCapacityModule();
                maintenanceCoverageDirty |= hasAreaEffects(kind);
            }
            case SET_TIER -> capacityClustersDirty |= kind.isCapacityModule();
            case SET_ENABLED -> maintenanceCoverageDirty |= hasAreaEffects(kind);
            case SET_PARALLEL -> {}
        }
    }

    private static boolean hasAreaEffects(FacilityModuleKind kind) {
        FacilityModuleRegistry.Definition definition = FacilityModuleRegistry.get(kind);
        return definition != null && !definition.areaEffects()
            .isEmpty();
    }

    public List<CapacityCluster> getCapacityClusters(FacilityModuleKind kind) {
        refreshLayoutVersion();
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
        refreshLayoutVersion();
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

    /** Layout replacement and structural edits invalidate both views, including already populated client caches. */
    private void refreshLayoutVersion() {
        if (layout == null || layoutVersion == layout.version()) return;
        layoutVersion = layout.version();
        capacityClustersDirty = true;
        maintenanceCoverageDirty = true;
    }

}
