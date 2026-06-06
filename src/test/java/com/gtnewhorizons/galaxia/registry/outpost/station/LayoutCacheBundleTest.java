package com.gtnewhorizons.galaxia.registry.outpost.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;

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

    @Test
    void affectedBy_returnsValidEnumSet_forAllCombinations() {
        for (MutationKind mutation : MutationKind.values()) {
            for (FacilityModuleKind kind : FacilityModuleKind.values()) {
                EnumSet<CacheKind> result = LayoutCacheBundle.affectedBy(mutation, kind);
                assertNotNull(result, "result must not be null for " + mutation + " x " + kind);
                switch (mutation) {
                    case PLACE, DECONSTRUCT -> {
                        assertTrue(
                            result.contains(CacheKind.DUPLICATE_COUNTS),
                            () -> "PLACE/DECONSTRUCT should contain DUPLICATE_COUNTS for " + mutation + " x " + kind);
                        if (kind.isCapacityModule()) {
                            assertTrue(
                                result.contains(CacheKind.CAPACITY_CLUSTERS),
                                () -> "PLACE/DECONSTRUCT should contain CAPACITY_CLUSTERS for capacity kind " + mutation
                                    + " x "
                                    + kind);
                        } else {
                            assertTrue(
                                !result.contains(CacheKind.CAPACITY_CLUSTERS),
                                () -> "PLACE/DECONSTRUCT should NOT contain CAPACITY_CLUSTERS for non-capacity kind "
                                    + mutation
                                    + " x "
                                    + kind);
                        }
                        if (kind == FacilityModuleKind.MAINTENANCE_BAY) {
                            assertTrue(
                                result.contains(CacheKind.MAINTENANCE_COVERAGE),
                                () -> "PLACE/DECONSTRUCT should contain MAINTENANCE_COVERAGE for MAINTENANCE_BAY "
                                    + mutation
                                    + " x "
                                    + kind);
                        } else {
                            assertTrue(
                                !result.contains(CacheKind.MAINTENANCE_COVERAGE),
                                () -> "PLACE/DECONSTRUCT should NOT contain MAINTENANCE_COVERAGE for non-MAINTENANCE_BAY "
                                    + mutation
                                    + " x "
                                    + kind);
                        }
                    }
                    case SET_TIER -> {
                        if (kind.isCapacityModule()) {
                            assertTrue(
                                result.contains(CacheKind.CAPACITY_CLUSTERS),
                                () -> "SET_TIER should contain CAPACITY_CLUSTERS for capacity kind " + kind);
                        } else {
                            assertTrue(
                                result.isEmpty(),
                                () -> "SET_TIER should return empty set for non-capacity kind " + kind
                                    + " but got "
                                    + result);
                        }
                    }
                    case SET_ENABLED -> {
                        if (kind == FacilityModuleKind.MAINTENANCE_BAY) {
                            assertTrue(
                                result.contains(CacheKind.MAINTENANCE_COVERAGE),
                                () -> "SET_ENABLED should contain MAINTENANCE_COVERAGE for MAINTENANCE_BAY " + kind);
                        } else {
                            assertTrue(
                                result.isEmpty(),
                                () -> "SET_ENABLED should return empty set for " + kind + " but got " + result);
                        }
                    }
                    case SET_PARALLEL -> assertTrue(
                        result.isEmpty(),
                        () -> mutation + " should return empty set for " + kind + " but got " + result);
                }
            }
        }
    }

    @Test
    void duplicateCountNeverGoesBelowZero() {
        LayoutCacheBundle bundle = new LayoutCacheBundle(null);

        // PLACE increments
        bundle.applyMutation(MutationKind.PLACE, FacilityModuleKind.STORAGE);
        assertEquals(1, bundle.duplicateCount(FacilityModuleKind.STORAGE));

        bundle.applyMutation(MutationKind.PLACE, FacilityModuleKind.STORAGE);
        assertEquals(2, bundle.duplicateCount(FacilityModuleKind.STORAGE));

        // DECONSTRUCT decrements
        bundle.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE);
        assertEquals(1, bundle.duplicateCount(FacilityModuleKind.STORAGE));

        bundle.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE);
        assertEquals(0, bundle.duplicateCount(FacilityModuleKind.STORAGE));

        // Extra DECONSTRUCT must not go below zero
        bundle.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE);
        assertEquals(0, bundle.duplicateCount(FacilityModuleKind.STORAGE));

        bundle.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE);
        assertEquals(0, bundle.duplicateCount(FacilityModuleKind.STORAGE));
    }

    @Test
    void duplicateCountsAreIndependentPerKind() {
        LayoutCacheBundle bundle = new LayoutCacheBundle(null);

        bundle.applyMutation(MutationKind.PLACE, FacilityModuleKind.STORAGE);
        bundle.applyMutation(MutationKind.PLACE, FacilityModuleKind.STORAGE);
        bundle.applyMutation(MutationKind.PLACE, FacilityModuleKind.TANK);

        assertEquals(2, bundle.duplicateCount(FacilityModuleKind.STORAGE));
        assertEquals(1, bundle.duplicateCount(FacilityModuleKind.TANK));
        assertEquals(0, bundle.duplicateCount(FacilityModuleKind.BATTERY));

        bundle.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE);
        assertEquals(1, bundle.duplicateCount(FacilityModuleKind.STORAGE));
        assertEquals(1, bundle.duplicateCount(FacilityModuleKind.TANK));
    }

    @Test
    void setTierDoesNotAffectDuplicateCounts() {
        LayoutCacheBundle bundle = new LayoutCacheBundle(null);

        bundle.applyMutation(MutationKind.PLACE, FacilityModuleKind.STORAGE);
        assertEquals(1, bundle.duplicateCount(FacilityModuleKind.STORAGE));

        // SET_TIER should not change duplicate counts
        bundle.applyMutation(MutationKind.SET_TIER, FacilityModuleKind.STORAGE);
        assertEquals(1, bundle.duplicateCount(FacilityModuleKind.STORAGE));
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
        layout.deconstruct(StationTileCoord.of(2, 0));
        cache.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE, b);
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

        layout.deconstruct(StationTileCoord.of(2, 0));
        cache.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE, c);
        List<CapacityCluster> clusters = cache.getCapacityClusters(FacilityModuleKind.STORAGE);
        assertEquals(2, clusters.size());
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
        cache.applyMutation(MutationKind.PLACE, FacilityModuleKind.STORAGE, bridge);
        List<CapacityCluster> clusters = cache.getCapacityClusters(FacilityModuleKind.STORAGE);
        assertEquals(1, clusters.size());
        assertEquals(
            3,
            clusters.get(0)
                .members()
                .size());
    }
}
