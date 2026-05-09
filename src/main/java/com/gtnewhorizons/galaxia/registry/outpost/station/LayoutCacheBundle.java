package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
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
                if (kind == FacilityModuleKind.MAINTENANCE_BAY) {
                    result.add(CacheKind.MAINTENANCE_COVERAGE);
                }
            }
            case SET_TIER -> {
                if (kind.isCapacityModule()) {
                    result.add(CacheKind.CAPACITY_CLUSTERS);
                }
            }
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

    /**
     * Module-aware mutation. For capacity modules, updates clusters incrementally
     * instead of falling back to full rebuild.
     */
    public void applyMutation(MutationKind mutation, FacilityModuleKind kind, ModuleInstance module) {
        EnumSet<CacheKind> caches = affectedBy(mutation, kind);
        if (caches.contains(CacheKind.CAPACITY_CLUSTERS) && layout != null) {
            // Do incremental update before the dirty flag is processed
            switch (mutation) {
                case PLACE -> onCapacityModuleAdded(kind, module);
                case DECONSTRUCT -> onCapacityModuleRemoved(kind, module);
                case SET_TIER -> onCapacityModuleChanged(kind, module);
                default -> capacityClustersDirty = true;
            }
            caches.remove(CacheKind.CAPACITY_CLUSTERS);
        }
        invalidate(caches, mutation, kind);
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

    // ── Incremental capacity cluster updates ──

    private void onCapacityModuleAdded(FacilityModuleKind kind, ModuleInstance module) {
        if (capacityClustersDirty) {
            rebuildCapacityClusters();
        }
        List<CapacityCluster> clusters = capacityClusters.computeIfAbsent(kind, k -> new ArrayList<>());
        StationTileCoord anchor = module.anchor();

        // Find all adjacent clusters (same-kind neighbors that already belong to clusters)
        List<CapacityCluster> adjacentClusters = new ArrayList<>();
        for (int i = 0; i < StationLayout.DX.length; i++) {
            int ndx = anchor.dx() + StationLayout.DX[i];
            int ndy = anchor.dy() + StationLayout.DY[i];
            if (ndx < StationTileCoord.MIN || ndx > StationTileCoord.MAX
                || ndy < StationTileCoord.MIN
                || ndy > StationTileCoord.MAX) continue;
            StationTileCoord ncoord = StationTileCoord.of(ndx, ndy);
            ModuleInstance neighbor = layout.moduleAt(ncoord);
            if (neighbor == null || neighbor.kind() != kind) continue;
            for (CapacityCluster cluster : clusters) {
                if (cluster.members()
                    .contains(neighbor.anchor()) && !adjacentClusters.contains(cluster)) {
                    adjacentClusters.add(cluster);
                }
            }
        }

        if (adjacentClusters.isEmpty()) {
            // No adjacent clusters — create a new one
            long cap = effectiveCapacityOf(module, layout);
            Set<StationTileCoord> members = new LinkedHashSet<>();
            members.add(anchor);
            clusters.add(new CapacityCluster(kind, Collections.unmodifiableSet(members), cap));
        } else if (adjacentClusters.size() == 1) {
            // Add to existing cluster
            CapacityCluster existing = adjacentClusters.get(0);
            clusters.remove(existing);
            CapacityCluster merged = addModuleToCluster(existing, module, layout);
            clusters.add(merged);
        } else {
            // Merge multiple adjacent clusters + new module
            clusters.removeAll(adjacentClusters);
            Set<StationTileCoord> mergedMembers = new LinkedHashSet<>();
            for (CapacityCluster c : adjacentClusters) {
                mergedMembers.addAll(c.members());
            }
            mergedMembers.add(anchor);
            long totalCap = 0;
            for (StationTileCoord memberCoord : mergedMembers) {
                ModuleInstance mi = layout.moduleAt(memberCoord);
                if (mi != null) totalCap += effectiveCapacityOf(mi, layout);
            }
            clusters.add(new CapacityCluster(kind, Collections.unmodifiableSet(mergedMembers), totalCap));
        }
    }

    private void onCapacityModuleRemoved(FacilityModuleKind kind, ModuleInstance module) {
        if (capacityClustersDirty) {
            rebuildCapacityClusters();
        }
        List<CapacityCluster> clusters = capacityClusters.get(kind);
        if (clusters == null) return;
        StationTileCoord anchor = module.anchor();

        // Find the cluster containing this module
        CapacityCluster containing = null;
        for (CapacityCluster c : clusters) {
            if (c.members()
                .contains(anchor)) {
                containing = c;
                break;
            }
        }
        if (containing == null) return;
        clusters.remove(containing);

        Set<StationTileCoord> remaining = new LinkedHashSet<>(containing.members());
        remaining.remove(anchor);
        if (remaining.isEmpty()) return;

        // Check if the cluster split — BFS from one remaining member, see if all reached
        Set<StationTileCoord> reachable = bfsReachable(
            layout,
            kind,
            remaining.iterator()
                .next(),
            remaining);
        Set<StationTileCoord> unreachable = new LinkedHashSet<>(remaining);
        unreachable.removeAll(reachable);

        // Create cluster for reachable part
        long reachableCap = 0;
        for (StationTileCoord c : reachable) {
            ModuleInstance mi = layout.moduleAt(c);
            if (mi != null) reachableCap += effectiveCapacityOf(mi, layout);
        }
        clusters.add(new CapacityCluster(kind, Collections.unmodifiableSet(reachable), reachableCap));

        // Create cluster for unreachable part (if any)
        if (!unreachable.isEmpty()) {
            long unreachableCap = 0;
            for (StationTileCoord c : unreachable) {
                ModuleInstance mi = layout.moduleAt(c);
                if (mi != null) unreachableCap += effectiveCapacityOf(mi, layout);
            }
            clusters.add(new CapacityCluster(kind, Collections.unmodifiableSet(unreachable), unreachableCap));
        }
    }

    private void onCapacityModuleChanged(FacilityModuleKind kind, ModuleInstance module) {
        if (capacityClustersDirty) {
            rebuildCapacityClusters();
            return;
        }
        List<CapacityCluster> clusters = capacityClusters.get(kind);
        if (clusters == null) return;
        StationTileCoord anchor = module.anchor();

        // Replace the cluster with recalculated capacity
        for (int i = 0; i < clusters.size(); i++) {
            CapacityCluster c = clusters.get(i);
            if (c.members()
                .contains(anchor)) {
                long totalCap = 0;
                for (StationTileCoord memberCoord : c.members()) {
                    ModuleInstance mi = layout.moduleAt(memberCoord);
                    if (mi != null) totalCap += effectiveCapacityOf(mi, layout);
                }
                clusters.set(i, new CapacityCluster(kind, c.members(), totalCap));
                return;
            }
        }
    }

    private static CapacityCluster addModuleToCluster(CapacityCluster cluster, ModuleInstance module,
        StationLayout layout) {
        Set<StationTileCoord> newMembers = new LinkedHashSet<>(cluster.members());
        newMembers.add(module.anchor());
        long totalCap = 0;
        for (StationTileCoord c : newMembers) {
            ModuleInstance mi = layout.moduleAt(c);
            if (mi != null) totalCap += effectiveCapacityOf(mi, layout);
        }
        return new CapacityCluster(cluster.kind(), Collections.unmodifiableSet(newMembers), totalCap);
    }

    private static Set<StationTileCoord> bfsReachable(StationLayout layout, FacilityModuleKind kind,
        StationTileCoord start, Set<StationTileCoord> candidates) {
        Set<StationTileCoord> visited = new HashSet<>();
        java.util.Deque<StationTileCoord> queue = new java.util.ArrayDeque<>();
        queue.addLast(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            StationTileCoord current = queue.removeFirst();
            for (int i = 0; i < StationLayout.DX.length; i++) {
                int ndx = current.dx() + StationLayout.DX[i];
                int ndy = current.dy() + StationLayout.DY[i];
                if (ndx < StationTileCoord.MIN || ndx > StationTileCoord.MAX
                    || ndy < StationTileCoord.MIN
                    || ndy > StationTileCoord.MAX) continue;
                StationTileCoord ncoord = StationTileCoord.of(ndx, ndy);
                if (!candidates.contains(ncoord) || !visited.add(ncoord)) continue;
                ModuleInstance neighbor = layout.moduleAt(ncoord);
                if (neighbor != null && neighbor.kind() == kind) {
                    queue.addLast(ncoord);
                }
            }
        }
        return visited;
    }

    private static long effectiveCapacityOf(ModuleInstance module, StationLayout layout) {
        if (!module.kind()
            .isCapacityModule()) return 0L;
        long base = module.baseCapacity();
        int neighbors = StationLayout.countOrthogonalNeighbors(layout, module.anchor(), module.kind());
        return Math.round(base * (1.0 + 0.5 * neighbors));
    }

    // ── Maintenance coverage (unchanged) ──

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
                addIfInBounds(coord, 0, -1);
                addIfInBounds(coord, 1, -1);
                addIfInBounds(coord, 1, 0);
                addIfInBounds(coord, 1, 1);
                addIfInBounds(coord, 0, 1);
                addIfInBounds(coord, -1, 1);
                addIfInBounds(coord, -1, 0);
                addIfInBounds(coord, -1, -1);
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
