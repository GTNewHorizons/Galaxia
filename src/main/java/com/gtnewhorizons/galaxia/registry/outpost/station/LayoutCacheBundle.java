package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

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
                if (kind == FacilityModuleKind.MAINTENANCE_BAY) {
                    result.add(CacheKind.MAINTENANCE_COVERAGE);
                }
            }
            // T3.4: SET_TIER invalidates CAPACITY_CLUSTERS for capacity modules
            case SET_TIER -> {
                if (kind.isCapacityModule()) {
                    result.add(CacheKind.CAPACITY_CLUSTERS);
                }
            }
            // T3.6: SET_ENABLED invalidates MAINTENANCE_COVERAGE for Maintenance Bay
            case SET_ENABLED -> {
                if (kind == FacilityModuleKind.MAINTENANCE_BAY) {
                    result.add(CacheKind.MAINTENANCE_COVERAGE);
                }
            }
            // TODO: To be implemented in T7.4
            case SET_PARALLEL -> {}
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

    /**
     * Returns a read-only view of the cached maintenance coverage coordinates.
     * Coverage is rebuilt from the layout when dirty.
     */
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
        layout.forEachAnchor((coord, module) -> {
            if (module.kind() == FacilityModuleKind.MAINTENANCE_BAY && module.enabled()) {
                addIfInBounds(coord, 0, -1); // N
                addIfInBounds(coord, 1, -1); // NE
                addIfInBounds(coord, 1, 0); // E
                addIfInBounds(coord, 1, 1); // SE
                addIfInBounds(coord, 0, 1); // S
                addIfInBounds(coord, -1, 1); // SW
                addIfInBounds(coord, -1, 0); // W
                addIfInBounds(coord, -1, -1); // NW
            }
        });
        maintenanceCoverageDirty = false;
    }

    private void addIfInBounds(StationTileCoord coord, int dx, int dy) {
        int nx = coord.dx() + dx;
        int ny = coord.dy() + dy;
        if (nx >= StationTileCoord.MIN && nx <= StationTileCoord.MAX
            && ny >= StationTileCoord.MIN
            && ny <= StationTileCoord.MAX) {
            maintenanceCoverage.add(StationTileCoord.of(nx, ny));
        }
    }

    private void invalidate(EnumSet<CacheKind> caches, MutationKind mutation, FacilityModuleKind kind) {
        if (caches.contains(CacheKind.DUPLICATE_COUNTS)) {
            switch (mutation) {
                case PLACE -> duplicateCounts.merge(kind, 1, Integer::sum);
                case DECONSTRUCT -> duplicateCounts.computeIfPresent(kind, (k, v) -> Math.max(0, v - 1));
                // T3.4: SET_TIER does not affect DUPLICATE_COUNTS
                case SET_TIER -> {}
                // T3.6: SET_ENABLED does not affect DUPLICATE_COUNTS
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
