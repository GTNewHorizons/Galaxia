package com.gtnewhorizons.galaxia.registry.outpost.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

final class StationLayoutTest {

    @BeforeAll
    static void initModules() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void removeTileForModuleRemovesOnlyMatchingModuleTile() {
        StationLayout layout = new StationLayout();
        ModuleInstance module = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), FacilityModuleKind.POWER, null, ModuleShape.SINGLE, ModuleTier.NONE);
        StationTileCoord coord = StationTileCoord.of(1, 0);
        layout.place(coord, new PlacedTile(module, StationTileState.UNDER_CONSTRUCTION));

        layout.removeTileForModule(module.id);

        assertFalse(layout.isOccupied(coord));
        assertTrue(layout.isOccupied(StationTileCoord.CORE));
    }

    @Test
    void deconstructThenRebuildElsewhereKeepsUnrelatedTiles() {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        StationLayout layout = station.stationLayout();
        assertNotNull(layout);

        ModuleInstance removed = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), FacilityModuleKind.POWER, null, ModuleShape.SINGLE, ModuleTier.NONE);
        ModuleInstance retained = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), FacilityModuleKind.MINER, null, ModuleShape.SINGLE, ModuleTier.EV);
        retained.initAnchor(StationTileCoord.of(0, 1));
        station.addModule(removed);
        station.addModule(retained);
        StationTileCoord removedCoord = StationTileCoord.of(1, 0);
        StationTileCoord retainedCoord = StationTileCoord.of(0, 1);
        layout.place(removedCoord, new PlacedTile(removed, StationTileState.OCCUPIED_OPERATIONAL));
        layout.place(retainedCoord, new PlacedTile(retained, StationTileState.OCCUPIED_OPERATIONAL));

        assertTrue(station.removeModule(removed.id));

        assertFalse(layout.isOccupied(removedCoord));
        assertTrue(layout.isOccupied(retainedCoord));
        assertTrue(layout.isOccupied(StationTileCoord.CORE));
        assertEquals(
            1,
            station.modules()
                .size());

        ModuleInstance rebuilt = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), FacilityModuleKind.HAMMER, null, ModuleShape.SINGLE, ModuleTier.EV);
        StationTileCoord rebuiltCoord = StationTileCoord.of(-1, 0);
        station.addModule(rebuilt);
        layout.place(rebuiltCoord, new PlacedTile(rebuilt, StationTileState.UNDER_CONSTRUCTION));

        assertTrue(layout.isOccupied(rebuiltCoord));
        assertTrue(layout.isOccupied(retainedCoord));
        assertTrue(layout.isOccupied(StationTileCoord.CORE));
        assertEquals(
            2,
            station.modules()
                .size());
    }

    @Test
    void placeModuleInstanceThrowsOnOverlapWithDifferentModule() {
        StationLayout layout = new StationLayout();
        ModuleInstance storage = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        ModuleInstance tank = FacilityModuleKind.TANK
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);

        layout.place(storage);
        // Placing a different module at overlapping coordinate must throw
        assertThrows(
            IllegalStateException.class,
            () -> layout.place(tank),
            "place(ModuleInstance) must throw on overlap with a different module");
        // Original module must still be in place
        assertTrue(layout.isOccupied(StationTileCoord.of(1, 0)));
        assertSame(storage, layout.moduleAt(StationTileCoord.of(1, 0)));
    }

    @Test
    void placeModuleInstanceAllowsSameModuleReplacement() {
        StationLayout layout = new StationLayout();
        ModuleInstance storage = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.QUAD_2x2, ModuleTier.HV);

        // First placement by single-tile (anchor only)
        layout.place(StationTileCoord.of(1, 0), new PlacedTile(storage, StationTileState.UNDER_CONSTRUCTION));
        // Second placement via ModuleInstance expands to all 4 tiles (same module — allowed)
        layout.place(storage);

        assertEquals(
            StationTileState.UNDER_CONSTRUCTION,
            layout.get(StationTileCoord.of(1, 0))
                .state());
        assertTrue(layout.isOccupied(StationTileCoord.of(2, 0)));
        assertTrue(layout.isOccupied(StationTileCoord.of(1, 1)));
        assertTrue(layout.isOccupied(StationTileCoord.of(2, 1)));
    }

    @Test
    void placeModuleInstanceSetsAnchor() {
        StationLayout layout = new StationLayout();
        // Create module with null anchor (simulating persistence decode path)
        ModuleInstance module = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), FacilityModuleKind.STORAGE, null, ModuleShape.SINGLE, ModuleTier.HV);

        assertNull(module.anchorOrNull(), "Module should have null anchor before placement");

        module.initAnchor(StationTileCoord.of(3, 3));
        layout.place(module);

        // Anchor must be preserved after place()
        assertNotNull(module.anchor());
        assertEquals(
            (byte) 3,
            module.anchor()
                .dx());
        assertEquals(
            (byte) 3,
            module.anchor()
                .dy());
        assertTrue(layout.isOccupied(StationTileCoord.of(3, 3)));
        assertSame(module, layout.moduleAt(StationTileCoord.of(3, 3)));
    }

    @Test
    void placeModuleInstanceExpandsMultiTileFootprint() {
        StationLayout layout = new StationLayout();
        ModuleInstance block = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(0, 0), ModuleShape.BLOCK_3x3, ModuleTier.HV);

        layout.place(block);

        // All 9 tiles must be occupied
        StationTileCoord[] tiles = ModuleShape.BLOCK_3x3.tiles(StationTileCoord.of(0, 0));
        assertEquals(9, tiles.length);
        for (StationTileCoord tile : tiles) {
            assertTrue(layout.isOccupied(tile), "Tile " + tile + " should be occupied");
            assertSame(block, layout.moduleAt(tile), "Tile " + tile + " should reference the same module");
        }
    }

    @Test
    void loadFromSnapshotThenPlaceModuleExpandsChildTiles() {
        // Simulate persistence load flow:
        // 1. load anchors via loadFromSnapshot
        // 2. expand footprints via place(ModuleInstance)
        StationLayout layout = new StationLayout();
        ModuleInstance quad = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), FacilityModuleKind.STORAGE, null, ModuleShape.QUAD_2x2, ModuleTier.HV);

        // Simulate module being decoded with null anchor, then anchor set from layout tile
        quad.initAnchor(StationTileCoord.of(5, 5));

        // Step 1: load anchor tile (as persistence does)
        java.util.Map<StationTileCoord, PlacedTile> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put(StationTileCoord.of(5, 5), new PlacedTile(quad, StationTileState.OCCUPIED_OPERATIONAL));
        layout.loadFromSnapshot(snapshot);

        // Only anchor exists at this point
        assertEquals(2, layout.size()); // CORE + anchor
        assertTrue(layout.isOccupied(StationTileCoord.of(5, 5)));
        assertFalse(layout.isOccupied(StationTileCoord.of(6, 5)));

        // Step 2: expand footprint via place(ModuleInstance) — must NOT throw
        layout.place(quad);

        // All 4 tiles now exist
        assertTrue(layout.isOccupied(StationTileCoord.of(5, 5)));
        assertTrue(layout.isOccupied(StationTileCoord.of(6, 5)));
        assertTrue(layout.isOccupied(StationTileCoord.of(5, 6)));
        assertTrue(layout.isOccupied(StationTileCoord.of(6, 6)));
    }

    @Test
    void forEachAnchorYieldsEachModuleOnce() {
        StationLayout layout = new StationLayout();
        ModuleInstance a = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        ModuleInstance b = FacilityModuleKind.TANK
            .create(StationTileCoord.of(2, 0), ModuleShape.QUAD_2x2, ModuleTier.HV);

        layout.place(a);
        layout.place(b);

        java.util.Set<StationTileCoord> anchors = new java.util.HashSet<>();
        layout.forEachAnchor((coord, module) -> anchors.add(coord));

        assertEquals(2, anchors.size());
        assertTrue(anchors.contains(StationTileCoord.of(1, 0)));
        assertTrue(anchors.contains(StationTileCoord.of(2, 0)));
        // Child tiles of QUAD_2x2 must NOT appear in forEachAnchor
        assertFalse(anchors.contains(StationTileCoord.of(3, 0)));
        assertFalse(anchors.contains(StationTileCoord.of(2, 1)));
    }
}
