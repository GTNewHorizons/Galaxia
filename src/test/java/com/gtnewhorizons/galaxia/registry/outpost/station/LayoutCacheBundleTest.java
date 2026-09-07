package com.gtnewhorizons.galaxia.registry.outpost.station;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class LayoutCacheBundleTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
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
        LayoutCacheBundle cache = new LayoutCacheBundle(layout);

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
        LayoutCacheBundle cache = new LayoutCacheBundle(layout);

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
        LayoutCacheBundle cache = new LayoutCacheBundle(layout);

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
        LayoutCacheBundle cache = new LayoutCacheBundle(layout);

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

        LayoutCacheBundle cache = new LayoutCacheBundle(layout);
        assertEquals(
            1,
            cache.getCapacityClusters(FacilityModuleKind.STORAGE)
                .size());

        // Remove b — cluster should shrink to just a
        layout.removeTileForModule(b.id);
        cache.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE);
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

        LayoutCacheBundle cache = new LayoutCacheBundle(layout);
        assertEquals(
            1,
            cache.getCapacityClusters(FacilityModuleKind.STORAGE)
                .size());

        layout.removeTileForModule(c.id);
        cache.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE);
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

        LayoutCacheBundle cache = new LayoutCacheBundle(layout);
        assertEquals(
            1,
            cache.getCapacityClusters(FacilityModuleKind.STORAGE)
                .size());

        layout.removeTileForModule(center.id);
        cache.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE);

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
        LayoutCacheBundle cache = new LayoutCacheBundle(layout);
        List<CapacityCluster> projected = cache.getCapacityClustersExcluding(FacilityModuleKind.STORAGE, removed.id);

        layout.removeTileForModule(removed.id);
        cache.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE);
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
        LayoutCacheBundle cache = new LayoutCacheBundle(layout);
        assertEquals(
            2,
            cache.getCapacityClusters(FacilityModuleKind.STORAGE)
                .size());

        ModuleInstance bridge = makeStorage(2, 0);
        layout.place(bridge);
        cache.applyMutation(MutationKind.PLACE, FacilityModuleKind.STORAGE);
        List<CapacityCluster> clusters = cache.getCapacityClusters(FacilityModuleKind.STORAGE);
        assertEquals(1, clusters.size());
        assertEquals(
            3,
            clusters.get(0)
                .members()
                .size());
    }
}
