package com.gtnewhorizons.galaxia.core.starmap.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.init.Items;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.network.AssetBuildModulePacket;
import com.gtnewhorizons.galaxia.core.network.AssetInventoryUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.AssetSyncPacket;
import com.gtnewhorizons.galaxia.core.network.AssetUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.LogisticsConfigUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class StarmapServerActionsTest {

    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
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
    void buildModuleRejectsMissingServerAsset() {
        AssetBuildModulePacket packet = new AssetBuildModulePacket();
        // Don't set assetId - will be null, should fail

        AssetSyncPacket result = packet.apply(TEAM, false);

        assertNull(result);
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
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        StationTileCoord coord = StationTileCoord.of(1, 0);

        com.gtnewhorizons.galaxia.core.network.AssetBuildModulePacket packet = com.gtnewhorizons.galaxia.core.network.AssetBuildModulePacket
            .create(facility.assetId, FacilityModuleKind.STORAGE, ModuleShape.SINGLE, ModuleTier.HV, true, coord);

        AssetSyncPacket result = packet.apply(TEAM, true);

        assertNotNull(result, "build must immediately echo sync data for the open GUI");
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
    void buildMinerUsesDefaultTwoByTwoFootprint() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        StationTileCoord coord = StationTileCoord.of(1, 0);

        AssetBuildModulePacket packet = AssetBuildModulePacket.create(
            facility.assetId,
            FacilityModuleKind.MINER,
            FacilityModuleKind.MINER.defaultShape(),
            FacilityModuleKind.MINER.defaultTier(),
            true,
            coord);

        AssetSyncPacket result = packet.apply(TEAM, true);

        assertNotNull(result, "miner build must sync the new 2x2 footprint");
        assertEquals(
            1,
            facility.modules()
                .size());
        assertEquals(
            ModuleShape.QUAD_2x2,
            facility.modules()
                .get(0)
                .shape());
        assertEquals(
            5,
            facility.stationLayout()
                .size());
    }

    @Test
    void buildModuleRejectsShapeThatDoesNotMatchModuleKind() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);

        AssetBuildModulePacket packet = AssetBuildModulePacket.create(
            facility.assetId,
            FacilityModuleKind.MINER,
            ModuleShape.SINGLE,
            FacilityModuleKind.MINER.defaultTier(),
            true,
            StationTileCoord.of(1, 0));

        AssetSyncPacket result = packet.apply(TEAM, true);

        assertNull(result);
        assertTrue(
            facility.modules()
                .isEmpty());
    }

    @Test
    void buildModulesAddsMultipleModulesAndReturnsImmediateFullSync() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        StationTileCoord first = StationTileCoord.of(1, 0);
        StationTileCoord second = StationTileCoord.of(0, 1);

        com.gtnewhorizons.galaxia.core.network.AssetBuildModulePacket packet = com.gtnewhorizons.galaxia.core.network.AssetBuildModulePacket
            .createMany(
                facility.assetId,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                true,
                List.of(first, second));

        AssetSyncPacket result = packet.apply(TEAM, true);

        assertNotNull(result, "batch build must immediately echo sync data for the open GUI");
        assertEquals(
            2,
            facility.modules()
                .size());
        assertEquals(
            first,
            facility.modules()
                .get(0)
                .anchor());
        assertEquals(
            second,
            facility.modules()
                .get(1)
                .anchor());
    }

    @Test
    void buildModulesAllowsTargetsChainedByEarlierTargets() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        StationTileCoord first = StationTileCoord.of(1, 0);
        StationTileCoord chained = StationTileCoord.of(2, 0);

        com.gtnewhorizons.galaxia.core.network.AssetBuildModulePacket packet = com.gtnewhorizons.galaxia.core.network.AssetBuildModulePacket
            .createMany(
                facility.assetId,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                true,
                List.of(first, chained));

        AssetSyncPacket result = packet.apply(TEAM, true);

        assertNotNull(result, "batch build should allow targets adjacent to earlier targets in the same batch");
        assertEquals(
            2,
            facility.modules()
                .size());
        assertEquals(
            first,
            facility.modules()
                .get(0)
                .anchor());
        assertEquals(
            chained,
            facility.modules()
                .get(1)
                .anchor());
    }

    @Test
    void buildModulesRejectsWholeBatchWhenAnyTargetIsInvalid() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);

        com.gtnewhorizons.galaxia.core.network.AssetBuildModulePacket packet = com.gtnewhorizons.galaxia.core.network.AssetBuildModulePacket
            .createMany(
                facility.assetId,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                true,
                List.of(StationTileCoord.of(1, 0), StationTileCoord.of(5, 5)));

        AssetSyncPacket result = packet.apply(TEAM, true);

        assertNull(result);
        assertTrue(
            facility.modules()
                .isEmpty());
    }

    @Test
    void renameAssetMutatesServerAndReturnsImmediateFullSync() {
        AutomatedFacility facility = addFacilityToServer();

        AssetUpdatePacket packet = AssetUpdatePacket.rename(facility.assetId, "Renamed Station");
        AssetSyncPacket result = packet.mutateNoChecks(TEAM, facility);

        assertTrue(result != null);
        assertEquals(
            "Renamed Station",
            CelestialAssetStore.SERVER.findAssetInternal(facility.assetId)
                .displayName());
    }

    @Test
    void startDeconstructionMutatesServerAndReturnsImmediateFullSync() {
        AutomatedFacility facility = addFacilityToServer();
        facility.updateStatus(Buildable.Status.CONSTRUCTION_SITE);

        AssetUpdatePacket packet = AssetUpdatePacket
            .create(facility.assetId, AssetUpdatePacket.Action.START_DECONSTRUCTION);
        AssetSyncPacket result = packet.mutateNoChecks(TEAM, facility);

        assertTrue(result != null);
        assertEquals(
            Buildable.Status.DECONSTRUCTION,
            CelestialAssetStore.SERVER.findAssetInternal(facility.assetId)
                .status());
    }

    @Test
    void cancelConstructionRemovesServerAssetAndReturnsRemovalSync() {
        AutomatedFacility facility = addFacilityToServer();
        facility.updateStatus(Buildable.Status.CONSTRUCTION_SITE);

        AssetUpdatePacket packet = AssetUpdatePacket
            .create(facility.assetId, AssetUpdatePacket.Action.CANCEL_CONSTRUCTION);
        AssetSyncPacket result = packet.mutateNoChecks(TEAM, facility);

        assertTrue(result != null);
        assertNull(CelestialAssetStore.SERVER.findAssetInternal(facility.assetId));
    }

    @Test
    void destroyAssetRemovesServerAssetAndReturnsRemovalSync() {
        AutomatedFacility facility = addFacilityToServer();

        AssetUpdatePacket packet = AssetUpdatePacket.create(facility.assetId, AssetUpdatePacket.Action.DESTROY_ASSET);
        AssetSyncPacket result = packet.mutateNoChecks(TEAM, facility);

        assertTrue(result != null);
        assertNull(CelestialAssetStore.SERVER.findAssetInternal(facility.assetId));
    }

    @Test
    void inventoryPacketApplyMutatesServerStoreNotClientMirror() {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = testResource();

        AssetSyncPacket result = AssetInventoryUpdatePacket.add(facility.assetId, resource, 32)
            .apply(TEAM, true);

        assertNotNull(result);
        assertEquals(32, facility.getItemAmount(resource));
        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
    }

    @Test
    void logisticsPacketApplyMutatesServerStoreWithOwnershipCheck() {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = testResource();
        LogisticsResourceConfig config = new LogisticsResourceConfig(4, 16, true, false);

        AssetSyncPacket result = new LogisticsConfigUpdatePacket(facility.assetId, resource, config).apply(TEAM);

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
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        return facility;
    }

    private static ItemStackWrapper testResource() {
        return new ItemStackWrapper(Items.diamond, 0, null);
    }
}
