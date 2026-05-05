package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;

import io.netty.buffer.Unpooled;

/**
 * Tests packet serialization round-trips and delta sync correctness.
 */
final class StationPacketRoundTripTest {

    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void init() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @BeforeEach
    void cleanStores() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @AfterEach
    void cleanStoresAfter() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @Test
    void fullSyncRoundTripPreservesLayoutTilesAndModules() {
        AutomatedFacility server = buildFacilityWithModules(2);

        AssetSyncPacket full = AssetSyncPacket.fullSync(server);
        var buf = Unpooled.buffer();
        full.toBytes(buf);
        AssetSyncPacket decoded = new AssetSyncPacket();
        decoded.fromBytes(buf);

        // Apply decoded full sync to a fresh client
        AutomatedFacility client = createFacility();
        applyFullSyncFromPacket(client, decoded);

        assertEquals(
            server.modules()
                .size(),
            client.modules()
                .size(),
            "client must have same module count");
        assertEquals(
            server.stationLayout()
                .size(),
            client.stationLayout()
                .size(),
            "client layout must have same tile count");
        assertTrue(
            client.stationLayout()
                .isOccupied(StationTileCoord.CORE),
            "CORE on client");
        assertTrue(
            client.stationLayout()
                .isOccupied(StationTileCoord.of(1, 0)),
            "[1,0] on client");
    }

    // ── Delta sync ──

    @Test
    void fullSyncRoundTripPreservesHammerVariant() {
        AutomatedFacility server = createFacility();
        ModuleInstance hammerModule = buildModule(server, FacilityModuleKind.HAMMER, StationTileCoord.of(1, 0));
        hammerModule.setTier(com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier.LuV);
        ((ModuleHammer) hammerModule.component()).setVariant(HammerVariant.BIG);

        AutomatedFacility client = createFacility();
        applyFullSyncFromPacket(client, roundTrip(AssetSyncPacket.fullSync(server)));

        ModuleHammer clientHammer = (ModuleHammer) client.modules()
            .get(0)
            .component();
        assertEquals(HammerVariant.BIG, clientHammer.variant());
    }

    @Test
    void fullSyncRoundTripPreservesMinerVoidChances() {
        AutomatedFacility server = createFacility();
        server.setMinerVoidChancePercent("ore:iron", 35);

        AutomatedFacility client = createFacility();
        applyFullSyncFromPacket(client, roundTrip(AssetSyncPacket.fullSync(server)));

        assertEquals(35, client.minerVoidChancePercent("ore:iron"));
    }

    @Test
    void moduleAddedDeltaPlacesLayoutTileOnClient() {
        // Server: facility with 1 module, create FULL_SYNC for client baseline
        AutomatedFacility server = buildFacilityWithModules(1);

        // Client: receive FULL_SYNC
        AutomatedFacility client = createFacility();
        applyFullSyncFromPacket(client, roundTrip(AssetSyncPacket.fullSync(server)));
        assertTrue(
            client.stationLayout()
                .isOccupied(StationTileCoord.of(1, 0)),
            "after FULL_SYNC, client must have [1,0] tile");

        // Server builds SECOND module
        StationTileCoord anchor2 = StationTileCoord.of(2, 0);
        ModuleInstance m2 = buildModule(server, FacilityModuleKind.TANK, anchor2);
        int idx = server.modules()
            .size() - 1;

        // Generate MODULE_ADDED delta and apply to client via the production code path
        AssetSyncPacket delta = AssetSyncPacket.moduleAdded(server.assetId, idx, m2);
        AssetSyncPacket.applyDeltaToFacility(client, roundTrip(delta));

        // CRITICAL: client must now have the layout tile for the new module
        assertTrue(
            client.stationLayout()
                .isOccupied(anchor2),
            "BUG: MODULE_ADDED delta must place layout tile on client — [2,0] should be occupied");
    }

    @Test
    void moduleRemovedDeltaClearsLayoutTileOnClient() {
        AutomatedFacility server = buildFacilityWithModules(1);
        AutomatedFacility client = createFacility();
        applyFullSyncFromPacket(client, roundTrip(AssetSyncPacket.fullSync(server)));

        StationTileCoord anchor = StationTileCoord.of(1, 0);
        assertTrue(
            client.stationLayout()
                .isOccupied(anchor),
            "precondition: client has [1,0] tile");

        // Server removes the module
        ModuleInstance module = server.modules()
            .get(0);
        server.removeModule(module.id);
        server.stationLayout()
            .removeTileForModule(module.id);

        // Send MODULE_REMOVED delta to client
        AssetSyncPacket delta = AssetSyncPacket.moduleRemoved(server.assetId, 0, module.id);
        AssetSyncPacket.applyDeltaToFacility(client, roundTrip(delta));

        assertEquals(
            0,
            client.modules()
                .size(),
            "client should have no modules after remove");
        assertFalse(
            client.stationLayout()
                .isOccupied(anchor),
            "client layout must free the removed module anchor");
        assertEquals(
            1,
            client.stationLayout()
                .size(),
            "client layout should keep only CORE after MODULE_REMOVED");
    }

    // ── Helpers ──

    private static AssetSyncPacket roundTrip(AssetSyncPacket pkt) {
        var buf = Unpooled.buffer();
        pkt.toBytes(buf);
        AssetSyncPacket decoded = new AssetSyncPacket();
        decoded.fromBytes(buf);
        return decoded;
    }

    private static AutomatedFacility createFacility() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.addInternal(TEAM, facility);
        return facility;
    }

    private static AutomatedFacility buildFacilityWithModules(int count) {
        AutomatedFacility facility = createFacility();
        for (int i = 0; i < count; i++) {
            buildModule(facility, FacilityModuleKind.STORAGE, StationTileCoord.of(1 + i, 0));
        }
        return facility;
    }

    private static ModuleInstance buildModule(AutomatedFacility facility, FacilityModuleKind kind,
        StationTileCoord anchor) {
        ModuleInstance module = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), kind, anchor, ModuleShape.SINGLE, kind.defaultTier());
        facility.addModule(module);
        StationLayout layout = facility.stationLayout();
        StationTileState state = StationTileState.fromModuleStatus(module.status());
        for (StationTileCoord coord : module.shape()
            .tiles(module.anchor())) {
            layout.place(coord, new PlacedTile(module, state));
        }
        return module;
    }

    private static void applyFullSyncFromPacket(AutomatedFacility client, AssetSyncPacket packet) {
        client.clearModules();
        client.inventory.clear();
        client.logisticsConfig.clear();
        StationLayout layout = client.stationLayout();
        if (layout != null) layout.loadFromSnapshot(java.util.Collections.emptyMap());

        for (AssetSyncPacket d : packet.fullSyncDeltas()) {
            AssetSyncPacket.applyDeltaToFacility(client, d);
        }
        client.setSyncRevision(packet.syncRevision());
    }
}
