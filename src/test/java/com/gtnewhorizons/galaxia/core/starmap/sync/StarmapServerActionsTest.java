package com.gtnewhorizons.galaxia.core.starmap.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

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

        boolean result = packet.apply(TEAM, false);

        assertFalse(result);
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
            .create(
                facility.assetId,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                true,
                ModulePlacement.at(coord));

        boolean result = packet.apply(TEAM, true);

        assertTrue(result, "valid build must be accepted");
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
    void buildModuleFactoryRejectsMissingModuleSpec() {
        CelestialAsset.ID assetId = CelestialAsset.ID.create();

        assertThrows(
            IllegalArgumentException.class,
            () -> AssetBuildModulePacket.create(
                assetId,
                null,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                true,
                ModulePlacement.at(StationTileCoord.of(1, 0))));
        assertThrows(
            IllegalArgumentException.class,
            () -> AssetBuildModulePacket.create(
                assetId,
                FacilityModuleKind.STORAGE,
                null,
                ModuleTier.HV,
                true,
                ModulePlacement.at(StationTileCoord.of(1, 0))));
        assertThrows(
            IllegalArgumentException.class,
            () -> AssetBuildModulePacket.create(
                assetId,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                null,
                true,
                ModulePlacement.at(StationTileCoord.of(1, 0))));
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
            ModulePlacement.at(coord));

        boolean result = packet.apply(TEAM, true);

        assertTrue(result, "valid miner build must be accepted");
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
    void buildModuleAppliesRequestedFootprintRotation() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        StationTileCoord anchor = StationTileCoord.of(2, 0);

        AssetBuildModulePacket packet = AssetBuildModulePacket.createManyWithSpec(
            facility.assetId,
            FacilityModuleKind.MINER,
            FacilityModuleKind.MINER.defaultShape(),
            FacilityModuleKind.MINER.defaultTier(),
            null,
            MinerFocusTier.NONE,
            (short) 0,
            true,
            List.of(new ModulePlacement(anchor, 1)));

        boolean result = packet.apply(TEAM, true);

        assertTrue(result, "valid rotated miner build must be accepted");
        ModuleInstance module = facility.modules()
            .get(0);
        assertEquals(1, module.rotation());
        assertTrue(
            facility.stationLayout()
                .isOccupied(StationTileCoord.of(1, 0)));
        assertNull(
            facility.stationLayout()
                .get(StationTileCoord.of(3, 0)));
    }

    @Test
    void buildModulesApplyRotationPerTarget() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        StationTileCoord first = StationTileCoord.of(1, 0);
        StationTileCoord second = StationTileCoord.of(4, 0);

        AssetBuildModulePacket packet = AssetBuildModulePacket.createManyWithSpec(
            facility.assetId,
            FacilityModuleKind.MACERATOR,
            FacilityModuleKind.MACERATOR.defaultShape(),
            FacilityModuleKind.MACERATOR.defaultTier(),
            null,
            MinerFocusTier.NONE,
            (short) 0,
            true,
            List.of(new ModulePlacement(first, 0), new ModulePlacement(second, 1)));
        ByteBuf encoded = Unpooled.buffer();
        packet.toBytes(encoded);
        AssetBuildModulePacket decoded = new AssetBuildModulePacket();
        decoded.fromBytes(encoded);

        boolean result = decoded.apply(TEAM, true);

        assertTrue(result, "valid batch build must be accepted");
        assertEquals(
            2,
            facility.modules()
                .size());
        assertEquals(
            0,
            facility.modules()
                .get(0)
                .rotation());
        assertEquals(
            1,
            facility.modules()
                .get(1)
                .rotation());
        assertTrue(
            facility.stationLayout()
                .isOccupied(StationTileCoord.of(1, 1)));
        assertTrue(
            facility.stationLayout()
                .isOccupied(StationTileCoord.of(3, 1)));
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
            ModulePlacement.at(StationTileCoord.of(1, 0)));

        boolean result = packet.apply(TEAM, true);

        assertFalse(result);
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
                List.of(ModulePlacement.at(first), ModulePlacement.at(second)));

        boolean result = packet.apply(TEAM, true);

        assertTrue(result, "valid batch build must be accepted");
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
                List.of(ModulePlacement.at(first), ModulePlacement.at(chained)));

        boolean result = packet.apply(TEAM, true);

        assertTrue(result, "batch build should allow targets adjacent to earlier targets in the same batch");
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
                List.of(ModulePlacement.at(StationTileCoord.of(1, 0)), ModulePlacement.at(StationTileCoord.of(5, 5))));

        boolean result = packet.apply(TEAM, true);

        assertFalse(result);
        assertTrue(
            facility.modules()
                .isEmpty());
    }

    @Test
    void buildMinerCanStartWithTargetFocusTierAndSettingsGroup() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        ModuleInstance source = FacilityModuleKind.MINER
            .create(StationTileCoord.of(5, 5), FacilityModuleKind.MINER.defaultShape(), ModuleTier.EV);
        facility.addModule(source);
        SettingsGroup group = facility.createSettingsGroupForModule(source, "Shared miners");

        AssetBuildModulePacket packet = AssetBuildModulePacket.createManyWithSpec(
            facility.assetId,
            FacilityModuleKind.MINER,
            FacilityModuleKind.MINER.defaultShape(),
            ModuleTier.LuV,
            null,
            MinerFocusTier.II,
            group.id(),
            true,
            List.of(ModulePlacement.at(StationTileCoord.of(1, 0))));

        boolean result = packet.apply(TEAM, true);

        assertTrue(result, "valid target-spec build must be accepted");
        ModuleInstance built = facility.modules()
            .get(1);
        assertEquals(ModuleTier.LuV, built.tier());
        assertEquals(group.id(), built.groupId());
        ModuleMiner miner = (ModuleMiner) built.component();
        assertEquals(MinerFocusTier.II, miner.focusTier());
        assertNull(miner.focusOreKeyOrNull());
        assertEquals(0, miner.focusAlignmentProgress());
    }

    @Test
    void copyBuildCopiesSourcePhysicalSpecAndRuntimeSettingsWithoutProgress() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        ModuleInstance source = FacilityModuleKind.MINER
            .create(StationTileCoord.of(5, 5), FacilityModuleKind.MINER.defaultShape(), ModuleTier.LuV);
        ModuleMiner sourceMiner = (ModuleMiner) source.component();
        sourceMiner.setFocus(MinerFocusTier.II, "ore:iron", 123);
        facility.addModule(source);
        facility.setMinerOreBlacklisted(source, "ore:iron", true);

        AssetBuildModulePacket packet = AssetBuildModulePacket.copyFromModule(
            facility.assetId,
            0,
            source.id,
            true,
            List.of(ModulePlacement.at(StationTileCoord.of(1, 0))));

        boolean result = packet.apply(TEAM, true);

        assertTrue(result, "valid copy build must be accepted");
        ModuleInstance copied = facility.modules()
            .get(1);
        assertEquals(source.kind(), copied.kind());
        assertEquals(source.shape(), copied.shape());
        assertEquals(source.tier(), copied.tier());
        assertTrue(facility.isMinerOreBlacklisted(copied, "ore:iron"));
        ModuleMiner copiedMiner = (ModuleMiner) copied.component();
        assertEquals(MinerFocusTier.II, copiedMiner.focusTier());
        assertEquals("ore:iron", copiedMiner.focusOreKeyOrNull());
        assertEquals(0, copiedMiner.focusAlignmentProgress());
    }

    @Test
    void renameAssetMutatesServerAndReturnsImmediateFullSync() {
        AutomatedFacility facility = addFacilityToServer();

        AssetUpdatePacket packet = AssetUpdatePacket.rename(facility.assetId, "Renamed Station");
        boolean result = packet.mutateNoChecks(TEAM, facility);

        assertTrue(result);
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
        boolean result = packet.mutateNoChecks(TEAM, facility);

        assertTrue(result);
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
        boolean result = packet.mutateNoChecks(TEAM, facility);

        assertTrue(result);
        assertNull(CelestialAssetStore.SERVER.findAssetInternal(facility.assetId));
    }

    @Test
    void destroyAssetRemovesServerAssetAndReturnsRemovalSync() {
        AutomatedFacility facility = addFacilityToServer();

        AssetUpdatePacket packet = AssetUpdatePacket.create(facility.assetId, AssetUpdatePacket.Action.DESTROY_ASSET);
        boolean result = packet.mutateNoChecks(TEAM, facility);

        assertTrue(result);
        assertNull(CelestialAssetStore.SERVER.findAssetInternal(facility.assetId));
    }

    @Test
    void inventoryPacketApplyMutatesServerStoreNotClientMirror() {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = testResource();

        boolean result = AssetInventoryUpdatePacket.add(facility.assetId, resource, 32)
            .apply(TEAM, true);

        assertTrue(result);
        assertEquals(32, facility.getItemAmount(resource));
        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
    }

    @Test
    void logisticsPacketApplyMutatesServerStoreWithOwnershipCheck() {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = testResource();
        LogisticsResourceConfig config = new LogisticsResourceConfig(4, 16, true, false);

        boolean result = new LogisticsConfigUpdatePacket(facility.assetId, resource, config).apply(TEAM);

        assertTrue(result);
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
