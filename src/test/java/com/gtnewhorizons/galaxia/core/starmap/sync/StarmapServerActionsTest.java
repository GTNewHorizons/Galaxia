package com.gtnewhorizons.galaxia.core.starmap.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.item.Item;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.network.AssetInventoryUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.LogisticsConfigUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StarmapServerActionsTest {

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
    void createAssetAddsToServerStoreAndReturnsImmediateFullSync() {
        StarmapActionPayload payload = StarmapActionPayload.createAsset(
            CelestialObjectId.MARS,
            "Mars Automated Station",
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        StarmapActionResult result = StarmapServerActions.apply(TEAM, payload);

        assertTrue(result.applied());
        assertNotNull(result.syncPacket(), "create must immediately echo sync data for the open GUI");
        CelestialAsset created = CelestialAssetStore.SERVER.allAssetsInternal()
            .get(0);
        assertInstanceOf(AutomatedFacility.class, created);
        assertEquals("Mars Automated Station", created.displayName());
    }

    @Test
    void buildModuleRejectsMissingServerAsset() {
        StarmapActionPayload payload = StarmapActionPayload.buildModule(
            CelestialAsset.ID.create(),
            FacilityModuleKind.STORAGE,
            ModuleShape.SINGLE,
            ModuleTier.HV,
            true,
            StationTileCoord.of(1, 0));

        StarmapActionResult result = StarmapServerActions.apply(TEAM, payload);

        assertFalse(result.applied());
        assertEquals("missing_asset", result.errorKey());
        assertTrue(
            CelestialAssetStore.SERVER.allAssetsInternal()
                .isEmpty());
    }

    @Test
    void buildModuleAddsServerModuleAndReturnsImmediateFullSync() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.addInternal(TEAM, facility);
        StationTileCoord coord = StationTileCoord.of(1, 0);

        StarmapActionPayload payload = StarmapActionPayload
            .buildModule(facility.assetId, FacilityModuleKind.STORAGE, ModuleShape.SINGLE, ModuleTier.HV, true, coord);

        StarmapActionResult result = StarmapServerActions.apply(TEAM, payload);

        assertTrue(result.applied());
        assertNotNull(result.syncPacket(), "build must immediately echo sync data for the open GUI");
        assertEquals(
            1,
            facility.modules()
                .size());
        assertEquals(
            coord,
            facility.modules()
                .get(0)
                .anchor());
    }

    @Test
    void renameAssetMutatesServerAndReturnsImmediateFullSync() {
        AutomatedFacility facility = addFacilityToServer();

        StarmapActionResult result = StarmapServerActions
            .apply(TEAM, StarmapActionPayload.renameAsset(facility.assetId, "Renamed Station"));

        assertTrue(result.applied());
        assertNotNull(result.syncPacket());
        assertEquals(
            "Renamed Station",
            CelestialAssetStore.SERVER.findAssetInternal(facility.assetId)
                .displayName());
    }

    @Test
    void startDeconstructionMutatesServerAndReturnsImmediateFullSync() {
        AutomatedFacility facility = addFacilityToServer();
        facility.updateStatus(Buildable.Status.CONSTRUCTION_SITE);

        StarmapActionResult result = StarmapServerActions
            .apply(TEAM, StarmapActionPayload.assetOnly(StarmapAction.START_DECONSTRUCTION, facility.assetId));

        assertTrue(result.applied());
        assertNotNull(result.syncPacket());
        assertEquals(
            Buildable.Status.DECONSTRUCTION,
            CelestialAssetStore.SERVER.findAssetInternal(facility.assetId)
                .status());
    }

    @Test
    void cancelConstructionRemovesServerAssetAndReturnsRemovalSync() {
        AutomatedFacility facility = addFacilityToServer();
        facility.updateStatus(Buildable.Status.CONSTRUCTION_SITE);

        StarmapActionResult result = StarmapServerActions
            .apply(TEAM, StarmapActionPayload.assetOnly(StarmapAction.CANCEL_CONSTRUCTION, facility.assetId));

        assertTrue(result.applied());
        assertNotNull(result.syncPacket());
        assertNull(CelestialAssetStore.SERVER.findAssetInternal(facility.assetId));
    }

    @Test
    void destroyAssetRemovesServerAssetAndReturnsRemovalSync() {
        AutomatedFacility facility = addFacilityToServer();

        StarmapActionResult result = StarmapServerActions
            .apply(TEAM, StarmapActionPayload.assetOnly(StarmapAction.DESTROY_ASSET, facility.assetId));

        assertTrue(result.applied());
        assertNotNull(result.syncPacket());
        assertNull(CelestialAssetStore.SERVER.findAssetInternal(facility.assetId));
    }

    @Test
    void inventoryPacketApplyMutatesServerStoreNotClientMirror() {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = testResource();

        Object result = AssetInventoryUpdatePacket
            .apply(TEAM, true, AssetInventoryUpdatePacket.add(facility.assetId, resource, 32));

        assertNotNull(result);
        assertEquals(32, facility.inventory.getAmount(resource));
        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
    }

    @Test
    void logisticsPacketApplyMutatesServerStoreWithOwnershipCheck() {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = testResource();
        LogisticsResourceConfig config = new LogisticsResourceConfig(4, 16, true, false);

        Object result = LogisticsConfigUpdatePacket
            .apply(TEAM, new LogisticsConfigUpdatePacket(facility.assetId, resource, config));

        assertNotNull(result);
        assertEquals(config, facility.logisticsConfig.get(resource));
        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
    }

    private static AutomatedFacility addFacilityToServer() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.addInternal(TEAM, facility);
        return facility;
    }

    private static ItemStackWrapper testResource() {
        return new ItemStackWrapper(new Item(), 0, null);
    }
}
