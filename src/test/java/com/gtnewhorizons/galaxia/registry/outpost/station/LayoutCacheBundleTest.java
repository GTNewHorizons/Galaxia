package com.gtnewhorizons.galaxia.registry.outpost.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.feature.ModuleFeatureModifierBuilder;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class LayoutCacheBundleTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    // Product contract: coverage and upkeep share adjacency, including partially overlapping footprints.
    @Test
    void areaCoverageAndUpkeepAgreeForOverlappingFootprintsAndDisabledSources() {
        ModuleInstance bay = FacilityModuleKind.MAINTENANCE_BAY
            .create(StationTileCoord.of(5, 5), ModuleShape.QUAD_2x2, ModuleTier.NONE);
        ModuleInstance target = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(6, 5), ModuleShape.QUAD_2x2, ModuleTier.HV);
        LayoutCacheBundle cache = new LayoutCacheBundle(null, List.of(bay, target));

        assertFalse(
            cache.getMaintenanceCoverage()
                .contains(StationTileCoord.of(6, 5)));
        assertTrue(
            cache.getMaintenanceCoverage()
                .contains(StationTileCoord.of(7, 5)));
        assertEquals(100, upkeepMultiplier(cache, bay));
        assertTrue(upkeepMultiplier(cache, target) < 100);

        bay.setEnabled(false);
        cache.invalidate();
        assertTrue(
            cache.getMaintenanceCoverage()
                .isEmpty());
        assertEquals(100, upkeepMultiplier(cache, target));
    }

    // Product contract: a source at the grid boundary affects only valid adjacent tiles.
    @Test
    void areaCoverageClipsToGridAndDoesNotStackIdenticalDiscounts() {
        ModuleInstance first = FacilityModuleKind.MAINTENANCE_BAY.create(
            StationTileCoord.of(StationTileCoord.MAX, StationTileCoord.MAX),
            ModuleShape.SINGLE,
            ModuleTier.NONE);
        ModuleInstance second = FacilityModuleKind.MAINTENANCE_BAY.create(
            StationTileCoord.of(StationTileCoord.MAX - 2, StationTileCoord.MAX),
            ModuleShape.SINGLE,
            ModuleTier.NONE);
        ModuleInstance target = makeStorage(StationTileCoord.MAX - 1, StationTileCoord.MAX);
        LayoutCacheBundle single = new LayoutCacheBundle(null, List.of(first));
        LayoutCacheBundle both = new LayoutCacheBundle(null, List.of(first, second));

        assertEquals(
            3,
            single.getMaintenanceCoverage()
                .size());
        assertEquals(upkeepMultiplier(single, target), upkeepMultiplier(both, target));
        assertTrue(upkeepMultiplier(both, target) < 100);
    }

    private static int upkeepMultiplier(LayoutCacheBundle cache, ModuleInstance target) {
        ModuleFeatureModifierBuilder builder = new ModuleFeatureModifierBuilder();
        cache.applyAreaEffects(target, builder);
        return builder.build(Map.of())
            .upkeepMultiplierPercent();
    }

    // Product contract: available capacity follows structural layout replacement.
    @Test
    void totalCapacityTracksLayoutReplacement() {
        StationLayout layout = new StationLayout();
        layout.place(makeStorage(1, 0));
        LayoutCacheBundle cache = new LayoutCacheBundle(layout, List.of());
        long initial = cache.totalCapacity(FacilityModuleKind.STORAGE);

        StationLayout replacement = new StationLayout();
        replacement.place(makeStorage(1, 0));
        replacement.place(makeStorage(2, 0));
        layout.loadFromSnapshot(replacement.snapshot());

        assertTrue(cache.totalCapacity(FacilityModuleKind.STORAGE) > 2 * initial);
        assertEquals(0L, cache.totalCapacity(FacilityModuleKind.HAMMER));
    }

    // ── Incremental capacity cluster tests ──

    private static ModuleInstance makeStorage(int x, int y) {
        return FacilityModuleKind.STORAGE.create(StationTileCoord.of(x, y), ModuleShape.SINGLE, ModuleTier.HV);
    }

    private static ModuleInstance makeStorage(int x, int y, ModuleTier tier) {
        return FacilityModuleKind.STORAGE.create(StationTileCoord.of(x, y), ModuleShape.SINGLE, tier);
    }

    @Test
    void singleModuleProducesOneCluster() {
        StationLayout layout = new StationLayout();
        ModuleInstance m = makeStorage(1, 0);
        layout.place(m);
        LayoutCacheBundle cache = new LayoutCacheBundle(layout, List.of());

        List<CapacityCluster> clusters = cache.getCapacityClusters(FacilityModuleKind.STORAGE);
        assertEquals(1, clusters.size());
        assertEquals(
            1,
            clusters.get(0)
                .members()
                .size());
        assertEquals(
            1024L,
            clusters.get(0)
                .effectiveCapacity()); // 1×, 0 neighbors
    }

    @Test
    void twoAdjacentModulesMergeIntoOneCluster() {
        StationLayout layout = new StationLayout();
        ModuleInstance a = makeStorage(1, 0);
        ModuleInstance b = makeStorage(2, 0);
        layout.place(a);
        layout.place(b);
        LayoutCacheBundle cache = new LayoutCacheBundle(layout, List.of());

        List<CapacityCluster> clusters = cache.getCapacityClusters(FacilityModuleKind.STORAGE);
        assertEquals(1, clusters.size());
        assertEquals(
            2,
            clusters.get(0)
                .members()
                .size());
        // Each: 1024 * (1 + 0.5*1) = 1536, total = 3072 = 3×1024
        assertEquals(
            3 * 1024L,
            clusters.get(0)
                .effectiveCapacity());
    }

    @Test
    void twoSeparatedModulesProduceTwoClusters() {
        StationLayout layout = new StationLayout();
        ModuleInstance a = makeStorage(1, 0);
        ModuleInstance b = makeStorage(5, 5);
        layout.place(a);
        layout.place(b);
        LayoutCacheBundle cache = new LayoutCacheBundle(layout, List.of());

        List<CapacityCluster> clusters = cache.getCapacityClusters(FacilityModuleKind.STORAGE);
        assertEquals(2, clusters.size());
    }

    @Test
    void tierChangeUpdatesEffectiveCapacity() {
        StationLayout layout = new StationLayout();
        ModuleInstance a = makeStorage(1, 0, ModuleTier.HV); // 1024
        ModuleInstance b = makeStorage(2, 0, ModuleTier.IV); // 16384
        layout.place(a);
        layout.place(b);
        LayoutCacheBundle cache = new LayoutCacheBundle(layout, List.of());

        List<CapacityCluster> clusters = cache.getCapacityClusters(FacilityModuleKind.STORAGE);
        assertEquals(1, clusters.size());
        // a: 1024 * 1.5 = 1536, b: 16384 * 1.5 = 24576, total = 26112
        assertEquals(
            26112L,
            clusters.get(0)
                .effectiveCapacity());
    }

    @Test
    void removeModuleFromClusterUpdatesCluster() {
        StationLayout layout = new StationLayout();
        ModuleInstance a = makeStorage(1, 0);
        ModuleInstance b = makeStorage(2, 0);
        layout.place(a);
        layout.place(b);

        LayoutCacheBundle cache = new LayoutCacheBundle(layout, List.of());
        assertEquals(
            1,
            cache.getCapacityClusters(FacilityModuleKind.STORAGE)
                .size());

        // Remove b — cluster should shrink to just a
        layout.removeTileForModule(b.id);
        List<CapacityCluster> clusters = cache.getCapacityClusters(FacilityModuleKind.STORAGE);
        assertEquals(1, clusters.size());
        assertEquals(
            1,
            clusters.get(0)
                .members()
                .size());
        assertEquals(
            1024L,
            clusters.get(0)
                .effectiveCapacity());
    }

    @Test
    void removeBridgeSplitsCluster() {
        // a - c - b (line of 3), remove c → a and b become separate clusters
        StationLayout layout = new StationLayout();
        ModuleInstance a = makeStorage(1, 0);
        ModuleInstance c = makeStorage(2, 0); // bridge
        ModuleInstance b = makeStorage(3, 0);
        layout.place(a);
        layout.place(c);
        layout.place(b);

        LayoutCacheBundle cache = new LayoutCacheBundle(layout, List.of());
        assertEquals(
            1,
            cache.getCapacityClusters(FacilityModuleKind.STORAGE)
                .size());

        layout.removeTileForModule(c.id);
        List<CapacityCluster> clusters = cache.getCapacityClusters(FacilityModuleKind.STORAGE);
        assertEquals(2, clusters.size());
    }

    @Test
    void removeCenterOfPlusSplitsClusterFourWays() {
        StationLayout layout = new StationLayout();
        ModuleInstance center = makeStorage(2, 2);
        layout.place(center);
        layout.place(makeStorage(2, 1));
        layout.place(makeStorage(3, 2));
        layout.place(makeStorage(2, 3));
        layout.place(makeStorage(1, 2));

        LayoutCacheBundle cache = new LayoutCacheBundle(layout, List.of());
        assertEquals(
            1,
            cache.getCapacityClusters(FacilityModuleKind.STORAGE)
                .size());

        layout.removeTileForModule(center.id);

        assertEquals(
            4,
            cache.getCapacityClusters(FacilityModuleKind.STORAGE)
                .size());
    }

    @Test
    void projectedOrdinaryRemovalMatchesActualCapacity() {
        StationLayout layout = new StationLayout();
        layout.place(makeStorage(1, 0));
        ModuleInstance removed = makeStorage(2, 0);
        layout.place(removed);

        assertProjectionMatchesActualRemoval(layout, removed, 1);
    }

    @Test
    void projectedBridgeRemovalMatchesActualCapacity() {
        StationLayout layout = new StationLayout();
        layout.place(makeStorage(1, 0));
        ModuleInstance removed = makeStorage(2, 0);
        layout.place(removed);
        layout.place(makeStorage(3, 0));

        assertProjectionMatchesActualRemoval(layout, removed, 2);
    }

    @Test
    void projectedPlusRemovalMatchesActualCapacity() {
        StationLayout layout = new StationLayout();
        ModuleInstance removed = makeStorage(2, 2);
        layout.place(removed);
        layout.place(makeStorage(2, 1));
        layout.place(makeStorage(3, 2));
        layout.place(makeStorage(2, 3));
        layout.place(makeStorage(1, 2));

        assertProjectionMatchesActualRemoval(layout, removed, 4);
    }

    private static void assertProjectionMatchesActualRemoval(StationLayout layout, ModuleInstance removed,
        int expectedClusterCount) {
        LayoutCacheBundle cache = new LayoutCacheBundle(layout, List.of());
        List<CapacityCluster> projected = cache.getCapacityClustersExcluding(FacilityModuleKind.STORAGE, removed.id);

        layout.removeTileForModule(removed.id);
        List<CapacityCluster> actual = cache.getCapacityClusters(FacilityModuleKind.STORAGE);

        assertEquals(expectedClusterCount, projected.size());
        assertEquals(Set.copyOf(actual), Set.copyOf(projected));
    }

    @Test
    void placingModuleAdjacentToTwoClustersMergesThem() {
        StationLayout layout = new StationLayout();
        // Cluster 1: storage at (1,0)
        // Cluster 2: storage at (3,0)
        // Place storage at (2,0) — connects both clusters into one
        ModuleInstance a = makeStorage(1, 0);
        ModuleInstance b = makeStorage(3, 0);
        layout.place(a);
        layout.place(b);
        LayoutCacheBundle cache = new LayoutCacheBundle(layout, List.of());
        assertEquals(
            2,
            cache.getCapacityClusters(FacilityModuleKind.STORAGE)
                .size());

        ModuleInstance bridge = makeStorage(2, 0);
        layout.place(bridge);
        List<CapacityCluster> clusters = cache.getCapacityClusters(FacilityModuleKind.STORAGE);
        assertEquals(1, clusters.size());
        assertEquals(
            3,
            clusters.get(0)
                .members()
                .size());
    }
}
