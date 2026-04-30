package com.gtnewhorizons.galaxia.registry.outpost.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleComponent;
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
    void nonCapacityComponentInCapacityModuleThrows() {
        StationLayout layout = new StationLayout();
        ModuleInstance module = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        // Sabotage: replace component with a non-ICapacityModule implementation
        module.setComponent(new ModuleComponent() {

            @Override
            public byte getParallel() {
                return 1;
            }

            @Override
            public void setParallel(byte parallel) {}
        });
        layout.place(module);

        assertThrows(
            IllegalStateException.class,
            () -> CapacityClusterBuilder.build(layout, FacilityModuleKind.STORAGE),
            "CapacityClusterBuilder must throw when a capacity module's component does not implement ICapacityModule");
    }
}
