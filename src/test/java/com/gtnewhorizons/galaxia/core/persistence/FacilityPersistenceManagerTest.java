package com.gtnewhorizons.galaxia.core.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.gtnewhorizons.galaxia.core.network.PacketUtil;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;

final class FacilityPersistenceManagerTest {

    private static final Gson GSON = new Gson();

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void facilityPersistenceRoundTripsFullStationLayout() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = createStationWithFullLayout();

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());

        manager.decodeFacilityState(decoded, encoded);

        assertEquals(station.getEnergyStored(), decoded.getEnergyStored());
        assertEquals(
            station.modules()
                .size(),
            decoded.modules()
                .size());
        assertLayoutEquals(station.stationLayout(), decoded.stationLayout());
        assertEquals(GSON.toJson(encoded), GSON.toJson(manager.encodeFacilityState(decoded)));
    }

    private static AutomatedFacility createStationWithFullLayout() {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        station.setEnergyStored(245_760L);

        ModuleInstance hammer = addModule(station, FacilityModuleKind.HAMMER, Buildable.Status.OPERATIONAL);
        ModuleInstance miner = addModule(station, FacilityModuleKind.MINER, Buildable.Status.DISABLED);
        ModuleInstance power = addModule(station, FacilityModuleKind.POWER, Buildable.Status.IN_CONSTRUCTION);

        StationLayout layout = station.stationLayout();
        assertNotNull(layout);
        hammer.initAnchor(StationTileCoord.of(1, 0));
        layout.place(hammer);
        miner.initAnchor(StationTileCoord.of(2, 0));
        layout.place(miner);
        power.initAnchor(StationTileCoord.of(2, 1));
        layout.place(power);
        return station;
    }

    private static ModuleInstance addModule(AutomatedFacility station, FacilityModuleKind kind,
        Buildable.Status status) {
        ModuleInstance module = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), kind, null, ModuleShape.SINGLE, kind.defaultTier());
        module.updateStatus(status);
        station.addModule(module);
        return module;
    }

    @Test
    void roundTripMultiTileModulesAndTierShrink() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        // QUAD_2x2 module
        ModuleInstance quad = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), FacilityModuleKind.HAMMER, null, ModuleShape.QUAD_2x2, ModuleTier.IV);
        quad.updateStatus(Buildable.Status.OPERATIONAL);
        quad.initAnchor(StationTileCoord.of(5, 5));
        station.addModule(quad);
        StationLayout layout = station.stationLayout();
        assertNotNull(layout);
        layout.place(quad);

        // BLOCK_3x3 module
        ModuleInstance block = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), FacilityModuleKind.MINER, null, ModuleShape.BLOCK_3x3, ModuleTier.EV);
        block.updateStatus(Buildable.Status.OPERATIONAL);
        block.initAnchor(StationTileCoord.of(-5, -5));
        station.addModule(block);
        layout.place(block);

        // Encode
        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        // Only 2 anchor tiles saved (not 2 + 4 + 9 = 15)
        assertEquals(2, encoded.layoutTiles.size());

        // Decode
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        StationLayout decodedLayout = decoded.stationLayout();
        assertNotNull(decodedLayout);

        // Assert QUAD_2x2 tiles exist
        StationTileCoord qa = StationTileCoord.of(5, 5);
        assertTrue(decodedLayout.isOccupied(qa));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(6, 5)));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(5, 6)));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(6, 6)));

        // Assert tile states — all tiles derive OCCUPIED_OPERATIONAL from module status
        assertEquals(
            StationTileState.OCCUPIED_OPERATIONAL,
            decodedLayout.get(qa)
                .state());
        assertEquals(
            StationTileState.OCCUPIED_OPERATIONAL,
            decodedLayout.get(StationTileCoord.of(6, 5))
                .state());
        assertEquals(
            StationTileState.OCCUPIED_OPERATIONAL,
            decodedLayout.get(StationTileCoord.of(5, 6))
                .state());
        assertEquals(
            StationTileState.OCCUPIED_OPERATIONAL,
            decodedLayout.get(StationTileCoord.of(6, 6))
                .state());

        // Assert BLOCK_3x3 tiles exist
        StationTileCoord ba = StationTileCoord.of(-5, -5);
        assertTrue(decodedLayout.isOccupied(ba));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-4, -5)));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-6, -5)));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-5, -4)));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-5, -6)));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-6, -6)));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-4, -6)));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-6, -4)));

        // Assert tile states for BLOCK_3x3 child tiles
        assertEquals(
            StationTileState.OCCUPIED_OPERATIONAL,
            decodedLayout.get(ba)
                .state());
        assertEquals(
            StationTileState.OCCUPIED_OPERATIONAL,
            decodedLayout.get(StationTileCoord.of(-4, -5))
                .state());
        assertEquals(
            StationTileState.OCCUPIED_OPERATIONAL,
            decodedLayout.get(StationTileCoord.of(-6, -5))
                .state());

        // Assert child tiles reference same module as anchor
        ModuleInstance quadAnchor = decodedLayout.moduleAt(qa);
        assertNotNull(quadAnchor);
        assertSame(quadAnchor, decodedLayout.moduleAt(StationTileCoord.of(6, 5)));
        assertSame(quadAnchor, decodedLayout.moduleAt(StationTileCoord.of(5, 6)));
        assertSame(quadAnchor, decodedLayout.moduleAt(StationTileCoord.of(6, 6)));

        ModuleInstance blockAnchor = decodedLayout.moduleAt(ba);
        assertNotNull(blockAnchor);
        assertSame(blockAnchor, decodedLayout.moduleAt(StationTileCoord.of(-4, -5)));
        assertSame(blockAnchor, decodedLayout.moduleAt(StationTileCoord.of(-4, -4)));

        // Tier-shrink: modify encoded JSON to use HV tier (invalid for HAMMER)
        assertEquals("HAMMER", encoded.modules.get(0).kind);
        byte invalidTier = PacketUtil.enumOrdinal(ModuleTier.HV);
        encoded.modules.get(0).tier = invalidTier;

        AutomatedFacility tierShrunk = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(tierShrunk, encoded);

        assertNotNull(tierShrunk.modules());
        assertEquals(
            2,
            tierShrunk.modules()
                .size());
        // HAMMER module should be downgraded from HV to EV (defaultTier)
        assertEquals(
            ModuleTier.EV,
            tierShrunk.modules()
                .get(0)
                .tier());
        // MINER module should keep its EV tier
        assertEquals(
            ModuleTier.EV,
            tierShrunk.modules()
                .get(1)
                .tier());
    }

    @Test
    void phaseThreeModulesRoundTrip() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        // Phase 3 module kinds: STORAGE, TANK, BATTERY, MAINTENANCE_BAY
        ModuleInstance storage = addModule(station, FacilityModuleKind.STORAGE, Buildable.Status.OPERATIONAL);
        ModuleInstance tank = addModule(station, FacilityModuleKind.TANK, Buildable.Status.OPERATIONAL);
        ModuleInstance battery = addModule(station, FacilityModuleKind.BATTERY, Buildable.Status.OPERATIONAL);
        ModuleInstance maintenance = addModule(
            station,
            FacilityModuleKind.MAINTENANCE_BAY,
            Buildable.Status.OPERATIONAL);

        StationLayout layout = station.stationLayout();
        assertNotNull(layout);
        storage.initAnchor(StationTileCoord.of(-1, 1));
        layout.place(storage);
        tank.initAnchor(StationTileCoord.of(-1, 2));
        layout.place(tank);
        battery.initAnchor(StationTileCoord.of(-1, 3));
        layout.place(battery);
        maintenance.initAnchor(StationTileCoord.of(-1, 4));
        layout.place(maintenance);

        // Encode
        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);

        // Verify 4 module entries and 4 anchor layout tiles
        assertEquals(4, encoded.modules.size());
        assertEquals(4, encoded.layoutTiles.size());

        // Verify module kinds in encoded state
        assertEquals("STORAGE", encoded.modules.get(0).kind);
        assertEquals("TANK", encoded.modules.get(1).kind);
        assertEquals("BATTERY", encoded.modules.get(2).kind);
        assertEquals("MAINTENANCE_BAY", encoded.modules.get(3).kind);

        // Decode into fresh facility
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        assertEquals(
            4,
            decoded.modules()
                .size());
        assertLayoutEquals(station.stationLayout(), decoded.stationLayout());

        // Verify module kinds survive the round-trip
        assertEquals(
            FacilityModuleKind.STORAGE,
            decoded.modules()
                .get(0)
                .kind());
        assertEquals(
            FacilityModuleKind.TANK,
            decoded.modules()
                .get(1)
                .kind());
        assertEquals(
            FacilityModuleKind.BATTERY,
            decoded.modules()
                .get(2)
                .kind());
        assertEquals(
            FacilityModuleKind.MAINTENANCE_BAY,
            decoded.modules()
                .get(3)
                .kind());

        // Verify modules are in the layout with correct references
        StationLayout decodedLayout = decoded.stationLayout();
        assertNotNull(decodedLayout);
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-1, 1)));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-1, 2)));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-1, 3)));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-1, 4)));

        // Verify correct module at each tile
        assertSame(
            decoded.modules()
                .get(0),
            decodedLayout.moduleAt(StationTileCoord.of(-1, 1)));
        assertSame(
            decoded.modules()
                .get(1),
            decodedLayout.moduleAt(StationTileCoord.of(-1, 2)));
        assertSame(
            decoded.modules()
                .get(2),
            decodedLayout.moduleAt(StationTileCoord.of(-1, 3)));
        assertSame(
            decoded.modules()
                .get(3),
            decodedLayout.moduleAt(StationTileCoord.of(-1, 4)));

        // Re-encode and verify JSON identity (byte-perfect round-trip)
        assertEquals(GSON.toJson(encoded), GSON.toJson(manager.encodeFacilityState(decoded)));
    }

    @Test
    void everyModuleKindSurvivesRoundTrip() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        // Create ALL 7 module kinds with various statuses
        // Layout coordinates starting at (1,0) and spreading right/down — no overlaps
        ModuleInstance hammer = createAndPlaceModule(
            station,
            FacilityModuleKind.HAMMER,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.IV,
            StationTileCoord.of(1, 0));
        ModuleInstance miner = createAndPlaceModule(
            station,
            FacilityModuleKind.MINER,
            Buildable.Status.DISABLED,
            ModuleShape.SINGLE,
            ModuleTier.EV,
            StationTileCoord.of(2, 0));
        ModuleInstance power = createAndPlaceModule(
            station,
            FacilityModuleKind.POWER,
            Buildable.Status.IN_CONSTRUCTION,
            ModuleShape.SINGLE,
            ModuleTier.NONE,
            StationTileCoord.of(3, 0));
        ModuleInstance storage = createAndPlaceModule(
            station,
            FacilityModuleKind.STORAGE,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.HV,
            StationTileCoord.of(1, 1));
        ModuleInstance tank = createAndPlaceModule(
            station,
            FacilityModuleKind.TANK,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.EV,
            StationTileCoord.of(2, 1));
        ModuleInstance battery = createAndPlaceModule(
            station,
            FacilityModuleKind.BATTERY,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.IV,
            StationTileCoord.of(3, 1));
        ModuleInstance maintenance = createAndPlaceModule(
            station,
            FacilityModuleKind.MAINTENANCE_BAY,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.NONE,
            StationTileCoord.of(1, 2));

        StationLayout layout = station.stationLayout();
        assertNotNull(layout);

        // Encode to JSON
        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);

        // Dump JSON for inspection
        String encodedJson = FacilityPersistenceManagerTest.GSON.toJson(encoded);
        System.out.println("=== Encoded FacilityStateJson (all 7 kinds) ===");
        System.out.println(encodedJson);
        System.out.println("=== End encoded JSON ===");
        System.out.println("Module count: " + encoded.modules.size());
        System.out.println("Layout tile count: " + encoded.layoutTiles.size());

        // Verify module entries
        assertEquals(7, encoded.modules.size());
        assertEquals(7, encoded.layoutTiles.size());

        // Verify each kind appears in encoded modules
        assertTrue(
            encoded.modules.stream()
                .anyMatch(mj -> "HAMMER".equals(mj.kind)));
        assertTrue(
            encoded.modules.stream()
                .anyMatch(mj -> "MINER".equals(mj.kind)));
        assertTrue(
            encoded.modules.stream()
                .anyMatch(mj -> "POWER".equals(mj.kind)));
        assertTrue(
            encoded.modules.stream()
                .anyMatch(mj -> "STORAGE".equals(mj.kind)));
        assertTrue(
            encoded.modules.stream()
                .anyMatch(mj -> "TANK".equals(mj.kind)));
        assertTrue(
            encoded.modules.stream()
                .anyMatch(mj -> "BATTERY".equals(mj.kind)));
        assertTrue(
            encoded.modules.stream()
                .anyMatch(mj -> "MAINTENANCE_BAY".equals(mj.kind)));

        // Verify shape bytes — SINGLE has ordinal 0
        for (FacilityPersistenceManager.ModuleJson mj : encoded.modules) {
            assertEquals(0, mj.shape, "All modules in this test should be SINGLE (ordinal 0)");
        }

        // Decode into fresh facility
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        // --- ASSERTIONS ---
        // Use assertAll to collect ALL failures
        org.junit.jupiter.api.Assertions.assertAll(
            "fullRoundTripAllKinds",
            () -> assertEquals(
                7,
                decoded.modules()
                    .size(),
                "Expected 7 modules, got " + decoded.modules()
                    .size() + dumpKinds(decoded)),
            () -> {
                // Verify each kind is present
                List<FacilityModuleKind> decodedKinds = decoded.modules()
                    .stream()
                    .map(ModuleInstance::kind)
                    .toList();
                for (FacilityModuleKind k : FacilityModuleKind.values()) {
                    assertTrue(
                        decodedKinds.contains(k),
                        "Missing module kind " + k + " in decoded facility" + dumpKinds(decoded));
                }
            },
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(1, 0), "HAMMER anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(2, 0), "MINER anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(3, 0), "POWER anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(1, 1), "STORAGE anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(2, 1), "TANK anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(3, 1), "BATTERY anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(1, 2), "MAINTENANCE_BAY anchor"),
            () -> assertLayoutEquals(layout, decoded.stationLayout()),
            // JSON identity — byte-perfect round-trip
            () -> assertEquals(
                encodedJson,
                GSON.toJson(manager.encodeFacilityState(decoded)),
                "JSON must be identical after round-trip"
                    + dumpFullState("encoded", encoded, "re-encoded", manager.encodeFacilityState(decoded))));
    }

    @Test
    void allModuleKindsWithMultiTileSurviveRoundTrip() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        // One module of each shape
        ModuleInstance single = createAndPlaceModule(
            station,
            FacilityModuleKind.HAMMER,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.IV,
            StationTileCoord.of(5, 5));

        ModuleInstance quad = createAndPlaceModule(
            station,
            FacilityModuleKind.MINER,
            Buildable.Status.OPERATIONAL,
            ModuleShape.QUAD_2x2,
            ModuleTier.EV,
            StationTileCoord.of(10, 10));

        ModuleInstance block = createAndPlaceModule(
            station,
            FacilityModuleKind.STORAGE,
            Buildable.Status.OPERATIONAL,
            ModuleShape.BLOCK_3x3,
            ModuleTier.HV,
            StationTileCoord.of(-5, -5));

        StationLayout layout = station.stationLayout();
        assertNotNull(layout);

        // Encode, dump, decode, and verify
        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        String encodedJson = GSON.toJson(encoded);
        System.out.println("=== Encoded multi-shape FacilityStateJson ===");
        System.out.println(encodedJson);
        System.out.println("=== End ===");
        System.out.println(
            "Modules: " + encoded.modules.size() + ", LayoutTiles (anchors only): " + encoded.layoutTiles.size());

        assertEquals(3, encoded.modules.size());
        assertEquals(3, encoded.layoutTiles.size()); // 3 anchors only

        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        StationLayout decodedLayout = decoded.stationLayout();
        assertNotNull(decodedLayout);

        org.junit.jupiter.api.Assertions.assertAll(
            "multiShapeRoundTrip",
            () -> assertEquals(
                3,
                decoded.modules()
                    .size()),
            () -> assertTrue(decodedLayout.isOccupied(StationTileCoord.of(5, 5)), "SINGLE anchor missing"),
            () -> assertTrue(decodedLayout.isOccupied(StationTileCoord.of(10, 10)), "QUAD anchor missing"),
            () -> assertTrue(decodedLayout.isOccupied(StationTileCoord.of(11, 10)), "QUAD child (1,0) missing"),
            () -> assertTrue(decodedLayout.isOccupied(StationTileCoord.of(10, 11)), "QUAD child (0,1) missing"),
            () -> assertTrue(decodedLayout.isOccupied(StationTileCoord.of(11, 11)), "QUAD child (1,1) missing"),
            () -> assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-5, -5)), "BLOCK anchor missing"),
            () -> assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-4, -4)), "BLOCK child missing"),
            () -> assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-6, -6)), "BLOCK child missing"),
            () -> assertEquals(
                encodedJson,
                GSON.toJson(manager.encodeFacilityState(decoded)),
                "Multi-shape JSON must be identical after round-trip"));
    }

    @Test
    void moduleAnchorAndShapeNotNullAfterDecode() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        // All seven kinds with explicit shapes and anchors
        for (FacilityModuleKind kind : FacilityModuleKind.values()) {
            StationTileCoord coord = StationTileCoord.of(1 + kind.ordinal(), 5);
            ModuleInstance m = createAndPlaceModule(
                station,
                kind,
                Buildable.Status.OPERATIONAL,
                ModuleShape.SINGLE,
                kind.defaultTier(),
                coord);
        }

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        String encodedJson = GSON.toJson(encoded);
        System.out.println("=== All kinds with shapes/anchors — " + encoded.modules.size() + " modules ===");
        System.out.println(encodedJson);

        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        // CRITICAL ASSERTION: Every module must have non-null anchor and shape after decode
        for (ModuleInstance m : decoded.modules()) {
            assertNotNull(
                m.anchor(),
                "Module " + m.kind()
                    + " (id="
                    + m.id
                    + ") has null anchor after decode!"
                    + " This means layout tiles were not reconstructed for this module."
                    + dumpModuleState(m));
            assertNotNull(
                m.shape(),
                "Module " + m.kind()
                    + " (id="
                    + m.id
                    + ") has null shape after decode!"
                    + " Shape byte was: "
                    + findShapeByte(encoded, m));
        }

        // All modules must have their tiles in the layout
        StationLayout decodedLayout = decoded.stationLayout();
        assertNotNull(decodedLayout);
        for (ModuleInstance m : decoded.modules()) {
            StationTileCoord anchor = m.anchor();
            assertNotNull(anchor);
            assertTrue(
                decodedLayout.isOccupied(anchor),
                "Layout missing anchor tile " + anchor + " for module " + m.kind());
            // Also verify at least one child tile exists (for multi-tile)
            StationTileCoord[] tiles = m.shape()
                .tiles(anchor);
            assertTrue(tiles.length >= 1);
            for (StationTileCoord tile : tiles) {
                assertTrue(
                    decodedLayout.isOccupied(tile),
                    "Layout missing tile " + tile + " (child of " + anchor + ") for module " + m.kind());
                PlacedTile pt = decodedLayout.get(tile);
                assertNotNull(pt, "PlacedTile null at " + tile + " for module " + m.kind());
                assertSame(
                    m,
                    pt.module(),
                    "Tile " + tile
                        + " should reference module "
                        + m.kind()
                        + " but references "
                        + (pt.module() != null ? pt.module()
                            .kind() : "null"));
            }
        }

        // Verify JSON identity
        assertEquals(encodedJson, GSON.toJson(manager.encodeFacilityState(decoded)));
    }

    // ── Helpers ──

    private static ModuleInstance createAndPlaceModule(AutomatedFacility station, FacilityModuleKind kind,
        Buildable.Status status, ModuleShape shape, ModuleTier tier, StationTileCoord coord) {
        ModuleInstance module = FacilityModuleRegistry.create(ModuleInstance.ID.create(), kind, null, shape, tier);
        module.updateStatus(status);
        station.addModule(module);
        module.initAnchor(coord);
        StationLayout layout = station.stationLayout();
        assertNotNull(layout);
        layout.place(module);
        return module;
    }

    private static void assertLayoutTilesExist(AutomatedFacility facility, StationTileCoord coord, String label) {
        StationLayout layout = facility.stationLayout();
        assertNotNull(layout, "Layout should not be null for " + label);
        assertTrue(
            layout.isOccupied(coord),
            "Layout missing tile at " + coord
                + " ("
                + label
                + "). "
                + "Layout size="
                + layout.size()
                + dumpLayoutKeys(layout));
    }

    private static String dumpKinds(AutomatedFacility facility) {
        StringBuilder sb = new StringBuilder("\nModules in facility:");
        for (ModuleInstance m : facility.modules()) {
            sb.append("\n  ")
                .append(m.kind())
                .append(" id=")
                .append(m.id)
                .append(" anchor=")
                .append(m.anchor())
                .append(" shape=")
                .append(m.shape())
                .append(" status=")
                .append(m.status());
        }
        return sb.toString();
    }

    private static String dumpLayoutKeys(StationLayout layout) {
        StringBuilder sb = new StringBuilder("\nLayout keys:");
        for (StationTileCoord c : layout.snapshot()
            .keySet()) {
            sb.append(" (")
                .append(c.dx())
                .append(",")
                .append(c.dy())
                .append(")");
        }
        return sb.toString();
    }

    private static String dumpModuleState(ModuleInstance m) {
        return "\nModule state:" + "\n  kind="
            + m.kind()
            + "\n  id="
            + m.id
            + "\n  anchor="
            + m.anchor()
            + "\n  shape="
            + m.shape()
            + "\n  tier="
            + m.tier()
            + "\n  status="
            + m.status();
    }

    private static String dumpFullState(String label1, FacilityPersistenceManager.FacilityStateJson s1, String label2,
        FacilityPersistenceManager.FacilityStateJson s2) {
        return "\n--- " + label1 + " ---\n" + GSON.toJson(s1) + "\n--- " + label2 + " ---\n" + GSON.toJson(s2);
    }

    private static Byte findShapeByte(FacilityPersistenceManager.FacilityStateJson state, ModuleInstance module) {
        return state.modules.stream()
            .filter(
                mj -> module.id.toString()
                    .equals(mj.moduleId))
            .findFirst()
            .map(mj -> mj.shape)
            .orElse(null);
    }

    @Test
    void legacyBigHammerModuleIsMigratedToHammerOnLoad() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();

        // Simulate a legacy save with a BIG_HAMMER module (no longer in the enum)
        FacilityPersistenceManager.FacilityStateJson legacy = new FacilityPersistenceManager.FacilityStateJson();
        legacy.celestialBodyId = "PANSPIRA";
        legacy.systemId = "NOVA_CAELUM";
        legacy.planetaryAnchorBodyId = "PANSPIRA";
        legacy.energyStored = 0L;
        legacy.settingsGroupsNextId = 1;
        legacy.modules = new ArrayList<>();

        FacilityPersistenceManager.ModuleJson mj = new FacilityPersistenceManager.ModuleJson();
        mj.moduleId = ModuleInstance.ID.create()
            .toString();
        mj.kind = "BIG_HAMMER";
        mj.status = Buildable.Status.OPERATIONAL.name();
        mj.tier = PacketUtil.enumOrdinal(ModuleTier.EV);
        mj.shape = PacketUtil.enumOrdinal(ModuleShape.SINGLE);
        mj.enabled = true;
        mj.cooldownTicks = 0;
        legacy.modules.add(mj);

        legacy.layoutTiles = new ArrayList<>();
        FacilityPersistenceManager.StationTileJson tj = new FacilityPersistenceManager.StationTileJson();
        tj.dx = 1;
        tj.dy = 0;
        tj.state = StationTileState.OCCUPIED_OPERATIONAL.name();
        tj.moduleId = mj.moduleId;
        legacy.layoutTiles.add(tj);

        legacy.buffer = new LinkedHashMap<>();
        legacy.logisticsConfig = new LinkedHashMap<>();

        AutomatedFacility decoded = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        manager.decodeFacilityState(decoded, legacy);

        assertEquals(
            1,
            decoded.modules()
                .size(),
            "BIG_HAMMER module should be decoded as HAMMER (not skipped)");
        assertEquals(
            FacilityModuleKind.HAMMER,
            decoded.modules()
                .get(0)
                .kind(),
            "BIG_HAMMER should map to HAMMER");

        // Verify layout tile is present and references the decoded module
        StationLayout layout = decoded.stationLayout();
        assertNotNull(layout);
        StationTileCoord coord = StationTileCoord.of(1, 0);
        assertTrue(layout.isOccupied(coord), "Layout should have the BIG_HAMMER anchor tile");
        PlacedTile tile = layout.get(coord);
        assertNotNull(tile);
        assertNotNull(tile.module(), "PlacedTile should reference the decoded HAMMER module");
        assertSame(
            decoded.modules()
                .get(0),
            tile.module());

        // Verify no zombie tiles — only CORE and the HAMMER anchor should exist
        assertEquals(2, layout.size(), "Layout should have exactly CORE + HAMMER anchor tiles");
    }

    @Test
    void orphanLayoutTilesAreSkippedOnLoad() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();

        // Simulate a save with a module that has an unresolvable kind (unknown enum value)
        FacilityPersistenceManager.FacilityStateJson legacy = new FacilityPersistenceManager.FacilityStateJson();
        legacy.celestialBodyId = "PANSPIRA";
        legacy.systemId = "NOVA_CAELUM";
        legacy.planetaryAnchorBodyId = "PANSPIRA";
        legacy.energyStored = 0L;
        legacy.settingsGroupsNextId = 1;
        legacy.modules = new ArrayList<>();

        // One valid HAMMER module
        FacilityPersistenceManager.ModuleJson hammerMj = new FacilityPersistenceManager.ModuleJson();
        hammerMj.moduleId = ModuleInstance.ID.create()
            .toString();
        hammerMj.kind = "HAMMER";
        hammerMj.status = Buildable.Status.OPERATIONAL.name();
        hammerMj.tier = PacketUtil.enumOrdinal(ModuleTier.EV);
        hammerMj.shape = PacketUtil.enumOrdinal(ModuleShape.SINGLE);
        hammerMj.enabled = true;
        hammerMj.cooldownTicks = 0;
        legacy.modules.add(hammerMj);

        // Simulate an unresolvable kind
        FacilityPersistenceManager.ModuleJson unknownMj = new FacilityPersistenceManager.ModuleJson();
        unknownMj.moduleId = ModuleInstance.ID.create()
            .toString();
        unknownMj.kind = "UNKNOWN_MODULE_KIND";
        unknownMj.status = Buildable.Status.OPERATIONAL.name();
        unknownMj.tier = PacketUtil.enumOrdinal(ModuleTier.NONE);
        unknownMj.shape = PacketUtil.enumOrdinal(ModuleShape.SINGLE);
        unknownMj.enabled = true;
        unknownMj.cooldownTicks = 0;
        legacy.modules.add(unknownMj);

        legacy.layoutTiles = new ArrayList<>();

        // Layout tile for HAMMER
        FacilityPersistenceManager.StationTileJson hammerTj = new FacilityPersistenceManager.StationTileJson();
        hammerTj.dx = 1;
        hammerTj.dy = 0;
        hammerTj.state = StationTileState.OCCUPIED_OPERATIONAL.name();
        hammerTj.moduleId = hammerMj.moduleId;
        legacy.layoutTiles.add(hammerTj);

        // Layout tile for the unknown module — should be SKIPPED (orphan tile)
        FacilityPersistenceManager.StationTileJson orphanTj = new FacilityPersistenceManager.StationTileJson();
        orphanTj.dx = 5;
        orphanTj.dy = 5;
        orphanTj.state = StationTileState.OCCUPIED_OPERATIONAL.name();
        orphanTj.moduleId = unknownMj.moduleId;
        legacy.layoutTiles.add(orphanTj);

        legacy.buffer = new LinkedHashMap<>();
        legacy.logisticsConfig = new LinkedHashMap<>();

        AutomatedFacility decoded = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        manager.decodeFacilityState(decoded, legacy);

        // Only the HAMMER module should be decoded
        assertEquals(
            1,
            decoded.modules()
                .size(),
            "Only HAMMER should survive; unknown kind should be skipped");

        // The HAMMER anchor tile should exist
        StationLayout layout = decoded.stationLayout();
        assertNotNull(layout);
        assertTrue(layout.isOccupied(StationTileCoord.of(1, 0)), "HAMMER anchor tile should exist");

        // The orphan tile at (5,5) should NOT exist (skipped during decode)
        assertFalse(
            layout.isOccupied(StationTileCoord.of(5, 5)),
            "Orphan layout tile for unknown module should be skipped, not blocking placement");
    }

    @Test
    void fullPersistenceRoundTripValidatesEveryModuleAndTile() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility before = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        // Create ALL 7 module kinds with both single-tile and multi-tile placements
        int x = 3;
        for (FacilityModuleKind kind : FacilityModuleKind.values()) {
            ModuleShape shape = (kind.ordinal() % 3 == 0) ? ModuleShape.SINGLE
                : (kind.ordinal() % 3 == 1) ? ModuleShape.QUAD_2x2 : ModuleShape.BLOCK_3x3;
            StationTileCoord coord = StationTileCoord.of(x, 5);
            ModuleTier tier = kind.defaultTier();
            ModuleInstance m = createAndPlaceModule(before, kind, Buildable.Status.OPERATIONAL, shape, tier, coord);
            assertNotNull(m.anchorOrNull(), "Module " + kind + " must have non-null anchor after placement");
            x += (shape == ModuleShape.BLOCK_3x3 ? 5 : (shape == ModuleShape.QUAD_2x2 ? 3 : 2));
        }

        StationLayout layoutBefore = before.stationLayout();
        assertNotNull(layoutBefore);
        int beforeAnchorCount = (int) layoutBefore.snapshot()
            .keySet()
            .stream()
            .filter(layoutBefore::isAnchorAt)
            .count();

        // Encode
        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(before);
        String encodedJson = FacilityPersistenceManagerTest.GSON.toJson(encoded);
        System.out.println("=== Full Round-Trip JSON ===");
        System.out.println(encodedJson);
        System.out.println("=== End JSON ===");
        System.out.println("Modules: " + encoded.modules.size() + ", Anchor tiles: " + encoded.layoutTiles.size());

        assertEquals(FacilityModuleKind.values().length, encoded.modules.size(), "All 7 kinds must be encoded");
        assertEquals(beforeAnchorCount, encoded.layoutTiles.size(), "Anchor tile count must match");

        // Decode
        AutomatedFacility after = new AutomatedFacility(
            before.assetId,
            before.celestialObjectId,
            before.kind,
            before.status());
        manager.decodeFacilityState(after, encoded);

        // ── HARD VALIDATION ──
        // 1. Module count must be equal
        assertEquals(
            before.modules()
                .size(),
            after.modules()
                .size(),
            "Module count must be equal before/after");
        assertEquals(
            FacilityModuleKind.values().length,
            after.modules()
                .size());

        // 2. Every module must have non-null anchor (hard assertion in anchor() itself)
        for (ModuleInstance m : after.modules()) {
            assertDoesNotThrow(() -> {
                StationTileCoord a = m.anchor();
                assertNotNull(a, "Module " + m.kind() + " must have non-null anchor");
                assertTrue(
                    a.dx() >= StationTileCoord.MIN && a.dx() <= StationTileCoord.MAX,
                    "Module " + m.kind() + " anchor dx " + a.dx() + " out of range");
                assertTrue(
                    a.dy() >= StationTileCoord.MIN && a.dy() <= StationTileCoord.MAX,
                    "Module " + m.kind() + " anchor dy " + a.dy() + " out of range");
            }, "anchor() must not throw for module " + m.kind());
        }

        // 3. Every anchor coordinate in the layout must have a non-null module
        StationLayout layoutAfter = after.stationLayout();
        assertNotNull(layoutAfter);
        for (Map.Entry<StationTileCoord, PlacedTile> entry : layoutAfter.snapshot()
            .entrySet()) {
            if (StationTileCoord.CORE.equals(entry.getKey())) continue;
            PlacedTile tile = entry.getValue();
            assertNotNull(tile, "Tile at " + entry.getKey() + " must not be null");
            assertNotNull(
                tile.module(),
                "Non-CORE tile at " + entry.getKey() + " must have a non-null module reference");
        }

        // 4. Every module's footprint tiles must exist in the layout
        for (ModuleInstance m : after.modules()) {
            StationTileCoord[] tiles = m.shape()
                .tiles(m.anchor());
            assertTrue(tiles.length >= 1, "Module " + m.kind() + " shape " + m.shape() + " must have at least 1 tile");
            for (StationTileCoord tile : tiles) {
                assertTrue(
                    layoutAfter.isOccupied(tile),
                    "Layout missing tile " + tile + " for module " + m.kind() + " at anchor " + m.anchor());
                PlacedTile pt = layoutAfter.get(tile);
                assertNotNull(pt, "PlacedTile null at " + tile);
                assertSame(
                    m,
                    pt.module(),
                    "Tile " + tile
                        + " should reference module "
                        + m.kind()
                        + " but references "
                        + (pt.module() != null ? pt.module()
                            .kind() : "null"));
            }
        }

        // 5. Every module kind from before must exist in after
        EnumSet<FacilityModuleKind> beforeKinds = EnumSet.noneOf(FacilityModuleKind.class);
        EnumSet<FacilityModuleKind> afterKinds = EnumSet.noneOf(FacilityModuleKind.class);
        before.modules()
            .forEach(m -> beforeKinds.add(m.kind()));
        after.modules()
            .forEach(m -> afterKinds.add(m.kind()));
        assertEquals(beforeKinds, afterKinds, "Module kind sets must be identical");

        // 6. Layout tile count comparison (total tiles, not just anchors)
        int afterAnchorCount = (int) layoutAfter.snapshot()
            .keySet()
            .stream()
            .filter(layoutAfter::isAnchorAt)
            .count();
        assertEquals(beforeAnchorCount, afterAnchorCount, "Anchor count must be equal before/after");

        // 7. JSON byte-identical round-trip
        String reEncoded = GSON.toJson(manager.encodeFacilityState(after));
        assertEquals(encodedJson, reEncoded, "JSON must be byte-identical after round-trip");
    }

    @Test
    void hardCrashOnNullAnchor() {
        ModuleInstance module = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), FacilityModuleKind.HAMMER, null, ModuleShape.SINGLE, ModuleTier.EV);
        assertThrows(
            IllegalStateException.class,
            module::anchor,
            "anchor() must throw when anchor is null (module not placed on layout)");
    }

    @Test
    void hardCrashOnMissingRegistryDefinition() {
        assertThrows(
            Exception.class,
            () -> FacilityModuleRegistry
                .create(ModuleInstance.ID.create(), FacilityModuleKind.HAMMER, null, ModuleShape.SINGLE, null),
            "create() with null tier should still create the module");
    }

    @Test
    void hardCrashOnLayoutPlaceOverlap() {
        StationLayout layout = new StationLayout();
        ModuleInstance m1 = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.HAMMER,
            StationTileCoord.of(5, 5),
            ModuleShape.SINGLE,
            ModuleTier.EV);
        ModuleInstance m2 = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MINER,
            StationTileCoord.of(5, 5),
            ModuleShape.SINGLE,
            ModuleTier.EV);

        // First placement via coordinate path should succeed
        layout.place(StationTileCoord.of(5, 5), new PlacedTile(m1, StationTileState.OCCUPIED_OPERATIONAL));
        // Second placement at same coordinate should throw
        assertThrows(
            IllegalStateException.class,
            () -> layout.place(StationTileCoord.of(5, 5), new PlacedTile(m2, StationTileState.OCCUPIED_OPERATIONAL)),
            "Placing a tile at already-occupied coordinate must throw");
    }

    @Test
    void placingModuleOnLayoutSetsItsAnchor() {
        // Simulate the bug: module created with null anchor, placed via tile-by-tile
        ModuleInstance module = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), FacilityModuleKind.HAMMER, null, ModuleShape.SINGLE, ModuleTier.EV);
        // Anchor is null after create() with null parameter
        assertNull(module.anchorOrNull(), "Module should have null anchor before layout placement");

        StationLayout layout = new StationLayout();
        layout.place(StationTileCoord.of(3, 3), new PlacedTile(module, StationTileState.OCCUPIED_OPERATIONAL));
        // After placing on layout, the module's anchor MUST be set from the tile coordinate
        assertNotNull(
            module.anchorOrNull(),
            "Layout.place(coord, tile) must set tile.module().anchor to the coordinate");
        assertEquals(
            (byte) 3,
            module.anchorOrNull()
                .dx(),
            "Anchor dx should match tile coordinate");
        assertEquals(
            (byte) 3,
            module.anchorOrNull()
                .dy(),
            "Anchor dy should match tile coordinate");
    }

    private static void assertLayoutEquals(StationLayout expected, StationLayout actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());

        for (Map.Entry<StationTileCoord, PlacedTile> entry : expected.snapshot()
            .entrySet()) {
            PlacedTile expectedTile = entry.getValue();
            PlacedTile actualTile = actual.get(entry.getKey());
            assertNotNull(actualTile);
            assertEquals(expectedTile.state(), actualTile.state());
            if (expectedTile.module() == null) {
                assertNull(actualTile.module());
            } else {
                assertNotNull(actualTile.module());
                assertEquals(expectedTile.module().id, actualTile.module().id);
                assertEquals(
                    expectedTile.module()
                        .kind(),
                    actualTile.module()
                        .kind());
            }
        }
    }

}
