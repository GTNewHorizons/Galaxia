package com.gtnewhorizons.galaxia.registry.outpost.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

final class CapacityClusterBuilderTest {

    private static final long HV_STORAGE_BASE = 1024L; // From ModuleStorage.baseCapacityForTier(HV)

    @BeforeAll
    static void init() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void nonCapacityKind_returnsEmptyList() {
        StationLayout layout = new StationLayout();
        assertTrue(
            CapacityClusterBuilder.build(layout, FacilityModuleKind.HAMMER)
                .isEmpty());
        assertTrue(
            CapacityClusterBuilder.build(layout, FacilityModuleKind.MINER)
                .isEmpty());
    }

    @Test
    void singleModule_hasOneTimesEffectiveCapacity() {
        StationLayout layout = new StationLayout();
        ModuleInstance module = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        layout.place(module);

        List<CapacityCluster> clusters = CapacityClusterBuilder.build(layout, FacilityModuleKind.STORAGE);

        assertEquals(1, clusters.size());
        CapacityCluster cluster = clusters.get(0);
        assertEquals(
            1,
            cluster.members()
                .size());
        // 1 module, 0 neighbors → 1024 * (1.0 + 0.5 * 0) = 1024
        assertEquals(HV_STORAGE_BASE, cluster.effectiveCapacity());
    }

    @Test
    void twoAdjacentModules_hasThreeTimesEffectiveCapacity() {
        StationLayout layout = new StationLayout();
        ModuleInstance a = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        ModuleInstance b = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(2, 0), ModuleShape.SINGLE, ModuleTier.HV);
        layout.place(a);
        layout.place(b);

        List<CapacityCluster> clusters = CapacityClusterBuilder.build(layout, FacilityModuleKind.STORAGE);

        assertEquals(1, clusters.size());
        CapacityCluster cluster = clusters.get(0);
        assertEquals(
            2,
            cluster.members()
                .size());
        // Each has 1 neighbor → each = 1024 * (1.0 + 0.5 * 1) = 1536, total = 3072 = 3 * 1024
        assertEquals(3 * HV_STORAGE_BASE, cluster.effectiveCapacity());
    }

    @Test
    void twoSeparatedModules_producesTwoClusters() {
        StationLayout layout = new StationLayout();
        ModuleInstance a = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        ModuleInstance b = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(3, 0), ModuleShape.SINGLE, ModuleTier.HV);
        layout.place(a);
        layout.place(b);

        List<CapacityCluster> clusters = CapacityClusterBuilder.build(layout, FacilityModuleKind.STORAGE);

        assertEquals(2, clusters.size());
        for (CapacityCluster cluster : clusters) {
            assertEquals(
                1,
                cluster.members()
                    .size());
            assertEquals(HV_STORAGE_BASE, cluster.effectiveCapacity());
        }
    }

    @Test
    void twoByTwoQuad_hasEightTimesEffectiveCapacity() {
        StationLayout layout = new StationLayout();
        // 2x2 quad at (1,0), (2,0), (1,1), (2,1)
        ModuleInstance a = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        ModuleInstance b = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(2, 0), ModuleShape.SINGLE, ModuleTier.HV);
        ModuleInstance c = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 1), ModuleShape.SINGLE, ModuleTier.HV);
        ModuleInstance d = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(2, 1), ModuleShape.SINGLE, ModuleTier.HV);
        layout.place(a);
        layout.place(b);
        layout.place(c);
        layout.place(d);

        List<CapacityCluster> clusters = CapacityClusterBuilder.build(layout, FacilityModuleKind.STORAGE);

        assertEquals(1, clusters.size());
        CapacityCluster cluster = clusters.get(0);
        assertEquals(
            4,
            cluster.members()
                .size());
        // Each of the 4 modules has 2 neighbors → each = 1024 * (1.0 + 0.5 * 2) = 2048, total = 8192 = 8 * 1024
        assertEquals(8 * HV_STORAGE_BASE, cluster.effectiveCapacity());
    }

    @Test
    void mixedKinds_doesNotCrossClusterWithDifferentKind() {
        StationLayout layout = new StationLayout();
        ModuleInstance storage = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        ModuleInstance tank = FacilityModuleKind.TANK
            .create(StationTileCoord.of(2, 0), ModuleShape.SINGLE, ModuleTier.HV);
        layout.place(storage);
        layout.place(tank);

        // STORAGE sees only itself
        List<CapacityCluster> storageClusters = CapacityClusterBuilder.build(layout, FacilityModuleKind.STORAGE);
        assertEquals(1, storageClusters.size());
        assertEquals(
            1,
            storageClusters.get(0)
                .members()
                .size());

        // TANK sees only itself
        List<CapacityCluster> tankClusters = CapacityClusterBuilder.build(layout, FacilityModuleKind.TANK);
        assertEquals(1, tankClusters.size());
        assertEquals(
            1,
            tankClusters.get(0)
                .members()
                .size());
    }

    @Test
    void threeInLine_hasCorrectEffectiveCapacity() {
        StationLayout layout = new StationLayout();
        ModuleInstance a = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        ModuleInstance b = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(2, 0), ModuleShape.SINGLE, ModuleTier.HV);
        ModuleInstance c = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(3, 0), ModuleShape.SINGLE, ModuleTier.HV);
        layout.place(a);
        layout.place(b);
        layout.place(c);

        List<CapacityCluster> clusters = CapacityClusterBuilder.build(layout, FacilityModuleKind.STORAGE);

        assertEquals(1, clusters.size());
        CapacityCluster cluster = clusters.get(0);
        assertEquals(
            3,
            cluster.members()
                .size());
        // a has 1 neighbor, b has 2 neighbors, c has 1 neighbor
        // a: 1024 * 1.5 = 1536, b: 1024 * 2.0 = 2048, c: 1024 * 1.5 = 1536
        // total = 5120
        assertEquals(5120L, cluster.effectiveCapacity());
    }

    @Test
    void nullComponentInCapacityModuleThrows() {
        StationLayout layout = new StationLayout();
        ModuleInstance module = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        // Sabotage: clear the component to simulate a corrupted module
        module.setComponent(null);
        layout.place(module);

        assertThrows(
            IllegalStateException.class,
            () -> CapacityClusterBuilder.build(layout, FacilityModuleKind.STORAGE),
            "CapacityClusterBuilder must throw when a capacity module has null component");
    }

    @Test
    void nonCapacityComponentInCapacityModuleStillWorks() {
        // Capacity is now read from registry tier data, not the component.
        StationLayout layout = new StationLayout();
        ModuleInstance module = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        module.setComponent(new IModuleComponent() {});
        layout.place(module);

        assertEquals(
            1024L,
            CapacityClusterBuilder.build(layout, FacilityModuleKind.STORAGE)
                .get(0)
                .effectiveCapacity());
    }

    @Test
    void baseCapacityMatchesTierData() {
        assertEquals(
            1024L,
            FacilityModuleKind.STORAGE.create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV)
                .baseCapacity());
        assertEquals(
            16_000L,
            FacilityModuleKind.TANK.create(StationTileCoord.of(2, 0), ModuleShape.SINGLE, ModuleTier.HV)
                .baseCapacity());
        assertEquals(
            100_000L,
            FacilityModuleKind.BATTERY.create(StationTileCoord.of(3, 0), ModuleShape.SINGLE, ModuleTier.HV)
                .baseCapacity());
    }

    @Test
    void maintenanceBayDoesNotImplementIParallelModule() {
        ModuleInstance bay = FacilityModuleKind.MAINTENANCE_BAY
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.NONE);
        IModuleComponent comp = bay.component();
        assertFalse(
            comp instanceof IParallelModule,
            "MaintenanceBay must not implement IParallelModule — it has no parallel mechanic");
    }

    @Test
    void layoutCacheBundle_capacityClustersForNonCapacityKindReturnsEmpty() {
        StationLayout layout = new StationLayout();
        ModuleInstance hammer = FacilityModuleKind.HAMMER
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.EV);
        layout.place(hammer);

        LayoutCacheBundle cache = new LayoutCacheBundle(layout);
        assertTrue(
            cache.getCapacityClusters(FacilityModuleKind.HAMMER)
                .isEmpty(),
            "Non-capacity kind should return empty clusters");
    }

    @Test
    void layoutCacheBundle_maintenanceCoverageReflectsEnabledState() {
        StationLayout layout = new StationLayout();
        ModuleInstance bay = FacilityModuleKind.MAINTENANCE_BAY
            .create(StationTileCoord.of(5, 5), ModuleShape.SINGLE, ModuleTier.NONE);
        layout.place(bay);

        LayoutCacheBundle cache = new LayoutCacheBundle(layout);
        Set<StationTileCoord> coverage = cache.getMaintenanceCoverage();
        assertEquals(8, coverage.size(), "Enabled bay should cover 8 surrounding tiles");
        assertTrue(coverage.contains(StationTileCoord.of(6, 5))); // E
        assertTrue(coverage.contains(StationTileCoord.of(5, 6))); // S

        // Disable bay, coverage should shrink
        bay.setEnabled(false);
        cache.applyMutation(MutationKind.SET_ENABLED, FacilityModuleKind.MAINTENANCE_BAY);
        assertTrue(
            cache.getMaintenanceCoverage()
                .isEmpty(),
            "Disabled bay should have no coverage");
    }

    @Test
    void layoutCacheBundle_capacityClustersRespectMultipleClusters() {
        StationLayout layout = new StationLayout();
        // Cluster 1: two adjacent storage at (1,0) and (2,0)
        ModuleInstance s1 = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        ModuleInstance s2 = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(2, 0), ModuleShape.SINGLE, ModuleTier.HV);
        // Cluster 2: single tank at (5,5)
        ModuleInstance t1 = FacilityModuleKind.TANK
            .create(StationTileCoord.of(5, 5), ModuleShape.SINGLE, ModuleTier.HV);
        layout.place(s1);
        layout.place(s2);
        layout.place(t1);

        LayoutCacheBundle cache = new LayoutCacheBundle(layout);
        List<CapacityCluster> storageClusters = cache.getCapacityClusters(FacilityModuleKind.STORAGE);
        List<CapacityCluster> tankClusters = cache.getCapacityClusters(FacilityModuleKind.TANK);

        assertEquals(1, storageClusters.size());
        assertEquals(1, tankClusters.size());
        assertEquals(
            2,
            storageClusters.get(0)
                .members()
                .size());
        assertEquals(
            1,
            tankClusters.get(0)
                .members()
                .size());
    }

    @Test
    void layoutCacheBundle_dirtyFlagPreventsRebuildOnRepeatedReads() {
        StationLayout layout = new StationLayout();
        ModuleInstance storage = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        layout.place(storage);

        LayoutCacheBundle cache = new LayoutCacheBundle(layout);
        List<CapacityCluster> first = cache.getCapacityClusters(FacilityModuleKind.STORAGE);
        List<CapacityCluster> second = cache.getCapacityClusters(FacilityModuleKind.STORAGE);
        // Same reference — cache was not rebuilt
        assertTrue(first == second, "Repeated reads should return the same cached list");
    }
}
