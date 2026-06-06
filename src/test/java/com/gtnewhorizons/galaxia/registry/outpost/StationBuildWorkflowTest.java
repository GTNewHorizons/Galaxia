package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationPlacementValidator;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

/**
 * Diagnostic: Station build workflow without network layer.
 * Tests the core facility/module/layout logic directly.
 */
final class StationBuildWorkflowTest {

    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    // ── Phase A — Facility foundation ──

    @Test
    void a1_freshFacilityHasCoreAndValidExpansionSlots() {
        AutomatedFacility facility = createFacility();
        StationLayout layout = facility.stationLayout();
        assertNotNull(layout, "facility must have a station layout");
        assertEquals(1, layout.size(), "fresh layout must have exactly 1 tile (CORE)");
        assertTrue(layout.isOccupied(StationTileCoord.CORE), "CORE must be occupied");

        // Verify expansion slots: exactly 4 orthogonal neighbors of CORE
        java.util.Set<StationTileCoord> slots = new java.util.LinkedHashSet<>();
        StationPlacementValidator.collectExpansionSlots(layout, slots);
        assertEquals(4, slots.size(), "must have exactly 4 expansion slots: [-1,0],[1,0],[0,-1],[0,1]");

        // [1,0] must be valid for placement
        StationTileCoord adjacent = StationTileCoord.of(1, 0);
        assertSame(
            StationPlacementValidator.Result.OK,
            StationPlacementValidator.validate(layout, adjacent),
            "[1,0] must be OK (adjacent to CORE)");

        // [7,5] is far from CORE — must NOT be occupied, must be REJECTED_NOT_ADJACENT
        StationTileCoord far = StationTileCoord.of(7, 5);
        assertFalse(layout.isOccupied(far), "[7,5] must NOT be occupied on fresh layout");
        assertSame(
            StationPlacementValidator.Result.REJECTED_NOT_ADJACENT,
            StationPlacementValidator.validate(layout, far),
            "[7,5] must be REJECTED_NOT_ADJACENT — BUG if REJECTED_OCCUPIED");
    }

    @Test
    void a2_buildModulePlacesTilesAndRejectsDoublePlacement() {
        AutomatedFacility facility = createFacility();
        StationLayout layout = facility.stationLayout();

        StationTileCoord anchor = StationTileCoord.of(1, 0);
        assertSame(StationPlacementValidator.Result.OK, StationPlacementValidator.validate(layout, anchor));

        // Simulate what StarmapServerActions does on server
        ModuleInstance module = buildModuleOnServer(facility, FacilityModuleKind.STORAGE, anchor);

        assertNotNull(module, "module must be created");
        assertEquals(2, layout.size(), "layout must have CORE + 1 module tile");
        assertTrue(layout.isOccupied(anchor), "tile at anchor must be occupied after build");
        assertEquals(module, layout.moduleAt(anchor), "moduleAt anchor must return the built module");
        assertEquals(
            1,
            facility.modules()
                .size(),
            "facility must have 1 module");

        // Double placement must be rejected
        assertSame(
            StationPlacementValidator.Result.REJECTED_OCCUPIED,
            StationPlacementValidator.validate(layout, anchor),
            "placing another module at same tile must be REJECTED_OCCUPIED");
    }

    @Test
    void a3_removeModuleFreesTile() {
        AutomatedFacility facility = createFacility();
        StationLayout layout = facility.stationLayout();
        StationTileCoord anchor = StationTileCoord.of(1, 0);
        ModuleInstance module = buildModuleOnServer(facility, FacilityModuleKind.STORAGE, anchor);

        // Remove module (server-side flow)
        facility.removeModule(module.id);
        layout.removeTileForModule(module.id);

        assertEquals(
            0,
            facility.modules()
                .size(),
            "no modules after removal");
        assertEquals(1, layout.size(), "only CORE after removal");
        assertFalse(layout.isOccupied(anchor), "tile must be free after removal");
        assertSame(
            StationPlacementValidator.Result.OK,
            StationPlacementValidator.validate(layout, anchor),
            "[1,0] must be OK for placement after removal");
    }

    @Test
    void a4_multiTileModulePlacesAllChildTiles() {
        AutomatedFacility facility = createFacility();
        StationLayout layout = facility.stationLayout();

        StationTileCoord anchor = StationTileCoord.of(2, 2);
        ModuleInstance module = buildModuleOnServer(facility, FacilityModuleKind.STORAGE, anchor, ModuleShape.QUAD_2x2);

        // All 4 tiles of the 2x2 footprint must be occupied
        assertTrue(layout.isOccupied(StationTileCoord.of(2, 2)), "[2,2] occupied");
        assertTrue(layout.isOccupied(StationTileCoord.of(3, 2)), "[3,2] occupied");
        assertTrue(layout.isOccupied(StationTileCoord.of(2, 3)), "[2,3] occupied");
        assertTrue(layout.isOccupied(StationTileCoord.of(3, 3)), "[3,3] occupied");

        assertEquals(5, layout.size(), "CORE + 4 child tiles = 5 total");
    }

    @Test
    void a5_minerDefaultFootprintIsTwoByTwo() {
        AutomatedFacility facility = createOutpost();
        StationLayout layout = facility.stationLayout();

        StationTileCoord anchor = StationTileCoord.of(1, 0);
        ModuleInstance module = buildModuleOnServer(facility, FacilityModuleKind.MINER, anchor);

        assertEquals(ModuleShape.QUAD_2x2, module.shape());
        assertTrue(layout.isOccupied(StationTileCoord.of(1, 0)), "[1,0] occupied");
        assertTrue(layout.isOccupied(StationTileCoord.of(2, 0)), "[2,0] occupied");
        assertTrue(layout.isOccupied(StationTileCoord.of(1, 1)), "[1,1] occupied");
        assertTrue(layout.isOccupied(StationTileCoord.of(2, 1)), "[2,1] occupied");
        assertEquals(5, layout.size(), "CORE + 2x2 miner footprint = 5 total");
    }

    // ── Phase B — Validator parity (server vs simulated client) ──

    @Test
    void b1_validatorAgreesBetweenServerAndReconstructedClient() {
        // Server: build 2 modules
        AutomatedFacility server = createFacility();
        buildModuleOnServer(server, FacilityModuleKind.STORAGE, StationTileCoord.of(1, 0));
        buildModuleOnServer(server, FacilityModuleKind.TANK, StationTileCoord.of(2, 0));

        // Client: independent facility, simulate receiving tiles via FULL_SYNC
        AutomatedFacility client = createFacility();
        StationLayout clientLayout = client.stationLayout();
        // Copy all tiles from server layout to client layout
        clientLayout.loadFromSnapshot(java.util.Collections.emptyMap());
        for (var entry : server.stationLayout()
            .snapshot()
            .entrySet()) {
            clientLayout.place(entry.getKey(), entry.getValue());
        }
        // Copy modules
        client.clearModules();
        for (ModuleInstance m : server.modules()) {
            client.addModule(m);
        }

        // Now verify validator parity for key coordinates
        StationTileCoord[] testCoords = { StationTileCoord.CORE, // occupied on both
            StationTileCoord.of(1, 0), // occupied on both
            StationTileCoord.of(2, 0), // occupied on both
            StationTileCoord.of(3, 0), // free, adjacent to [2,0] — OK
            StationTileCoord.of(-1, 0), // free, adjacent to CORE — OK
            StationTileCoord.of(7, 5), // far away — REJECTED_NOT_ADJACENT
        };

        for (StationTileCoord coord : testCoords) {
            StationPlacementValidator.Result serverResult = StationPlacementValidator
                .validate(server.stationLayout(), coord);
            StationPlacementValidator.Result clientResult = StationPlacementValidator.validate(clientLayout, coord);
            assertSame(
                serverResult,
                clientResult,
                "validator mismatch at " + coord + " — server=" + serverResult + " client=" + clientResult);
        }
    }

    // ── Helpers ──

    private static AutomatedFacility createFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static AutomatedFacility createOutpost() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
    }

    /** Simulates what StarmapServerActions does server-side. */
    private static ModuleInstance buildModuleOnServer(AutomatedFacility facility, FacilityModuleKind kind,
        StationTileCoord anchor) {
        return buildModuleOnServer(facility, kind, anchor, kind.defaultShape());
    }

    private static ModuleInstance buildModuleOnServer(AutomatedFacility facility, FacilityModuleKind kind,
        StationTileCoord anchor, ModuleShape shape) {
        ModuleInstance module = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), kind, anchor, shape, kind.defaultTier());
        facility.addModule(module);

        StationLayout layout = facility.stationLayout();
        StationTileState state = StationTileState.fromModuleStatus(module.status());
        for (StationTileCoord coord : module.shape()
            .tiles(module.anchor())) {
            layout.place(coord, new PlacedTile(module, state));
        }
        return module;
    }
}
