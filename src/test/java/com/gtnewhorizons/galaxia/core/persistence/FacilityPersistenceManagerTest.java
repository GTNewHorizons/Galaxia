package com.gtnewhorizons.galaxia.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
