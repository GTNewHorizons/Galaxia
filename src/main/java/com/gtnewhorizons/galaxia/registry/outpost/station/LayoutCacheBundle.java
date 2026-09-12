package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.feature.ModuleFeatureModifierBuilder;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public final class LayoutCacheBundle {

    private final @Nullable StationLayout layout;
    private final List<ModuleInstance> modules;
    private long layoutVersion = Long.MIN_VALUE;
    private long revision;

    private final Map<FacilityModuleKind, List<CapacityCluster>> capacityClusters = new EnumMap<>(
        FacilityModuleKind.class);
    private boolean capacityClustersDirty = true;
    private final Map<FacilityModuleKind, Long> capacityTotals = new EnumMap<>(FacilityModuleKind.class);

    private Map<StationTileCoord, Integer> upkeepMultipliers = Map.of();
    private boolean maintenanceCoverageDirty = true;

    public LayoutCacheBundle(@Nullable StationLayout layout, List<ModuleInstance> modules) {
        this.layout = layout;
        this.modules = modules;
    }

    public void invalidate() {
        capacityClustersDirty = true;
        maintenanceCoverageDirty = true;
        revision++;
    }

    public long revision() {
        refreshLayoutVersion();
        return revision;
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

    public long totalCapacity(FacilityModuleKind kind) {
        getCapacityClusters(kind);
        return capacityTotals.getOrDefault(kind, 0L);
    }

    private void rebuildCapacityClusters() {
        capacityClusters.clear();
        capacityTotals.clear();
        if (layout == null) {
            capacityClustersDirty = false;
            return;
        }
        for (FacilityModuleKind k : FacilityModuleKind.values()) {
            if (k.isCapacityModule()) {
                List<CapacityCluster> clusters = new ArrayList<>(CapacityClusterBuilder.build(layout, k));
                capacityClusters.put(k, clusters);
                long total = 0L;
                for (CapacityCluster cluster : clusters) total += cluster.effectiveCapacity();
                capacityTotals.put(k, total);
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
        return upkeepMultipliers.keySet();
    }

    public void applyAreaEffects(ModuleInstance target, ModuleFeatureModifierBuilder builder) {
        if (target == null || target.anchorOrNull() == null) return;
        getMaintenanceCoverage();
        for (StationTileCoord tile : target.tiles()) {
            Integer multiplier = upkeepMultipliers.get(tile);
            if (multiplier != null) builder.minUpkeepMultiplierPercent(multiplier);
        }
    }

    private void rebuildMaintenanceCoverage() {
        Map<StationTileCoord, Integer> multipliers = new HashMap<>();
        for (ModuleInstance module : modules) {
            module.areaEffects()
                .forEach(
                    effect -> effect.collectAffectedTiles(
                        module,
                        tile -> multipliers.merge(tile, effect.upkeepMultiplierPercent(), Math::min)));
        }
        upkeepMultipliers = Map.copyOf(multipliers);
        maintenanceCoverageDirty = false;
    }

    /** Layout replacement and structural edits invalidate both views, including already populated client caches. */
    private void refreshLayoutVersion() {
        if (layout == null || layoutVersion == layout.version()) return;
        layoutVersion = layout.version();
        invalidate();
    }

}
