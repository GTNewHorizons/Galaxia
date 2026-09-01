package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryBounds;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleDeconstructionOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeList;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepAmount;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepSettlement;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Tests packet serialization round-trips and delta sync correctness.
 */
final class StationPacketRoundTripTest {

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
        hammerModule.setTier(ModuleTier.LuV);
        ModuleHammer serverHammer = (ModuleHammer) hammerModule.component();
        serverHammer.setVariant(HammerVariant.BIG);
        serverHammer.setEnergyStored(123_456L);

        AutomatedFacility client = createFacility();
        applyFullSyncFromPacket(client, roundTrip(AssetSyncPacket.fullSync(server)));

        ModuleHammer clientHammer = (ModuleHammer) client.modules()
            .get(0)
            .component();
        assertEquals(HammerVariant.BIG, clientHammer.variant());
        assertEquals(123_456L, clientHammer.energyStored());
    }

    @Test
    void fullSyncRoundTripPreservesUpkeepCredits() {
        AutomatedFacility server = createFacility();
        ItemStackWrapper resource = new ItemStackWrapper(Items.diamond, 0, null);
        server.loadUpkeepCredits(new UpkeepSettlement.Credits(Map.of(resource, UpkeepAmount.parse("0.5")), Map.of()));

        AssetStateSync.Client.handleFull(roundTrip(AssetSyncPacket.fullSync(server)));

        AutomatedFacility client = (AutomatedFacility) CelestialAssetStore.CLIENT.findAssetInternal(server.assetId);
        assertNotNull(client);
        assertEquals(
            "0.5",
            client.upkeepCredits()
                .itemCredit(resource)
                .toDisplayString());
    }

    @Test
    void fullSyncRoundTripPreservesModuleOperation() {
        AutomatedFacility server = createFacility();
        ModuleInstance hammerModule = buildModule(server, FacilityModuleKind.HAMMER, StationTileCoord.of(1, 0));
        hammerModule.setOperation(
            ModuleOperationState.waiting(
                new ModuleOperationPlan(new HammerModuleOperation(ModuleTier.LuV, "BIG"), 200, Map.of(), true)));

        AutomatedFacility client = createFacility();
        applyFullSyncFromPacket(client, roundTrip(AssetSyncPacket.fullSync(server)));

        ModuleInstance clientModule = client.modules()
            .get(0);
        assertNotNull(clientModule.operationOrNull());
        assertEquals(
            ModuleTier.LuV,
            clientModule.operationOrNull()
                .plan()
                .spec()
                .targetTier());
    }

    @Test
    void fullSyncRoundTripPreservesPendingDeconstructionRefund() {
        AutomatedFacility server = createFacility();
        ModuleInstance module = buildModule(server, FacilityModuleKind.POWER, StationTileCoord.of(1, 0));
        module.updateStatus(Buildable.Status.DECONSTRUCTION);
        module.setOperation(ModuleOperationState.deconstructing(Map.of("minecraft:gold_ingot:0", 7L)));

        AutomatedFacility client = createFacility();
        applyFullSyncFromPacket(client, roundTrip(AssetSyncPacket.fullSync(server)));

        ModuleInstance clientModule = client.modules()
            .get(0);
        assertEquals(Buildable.Status.DECONSTRUCTION, clientModule.status());
        assertEquals(
            7L,
            clientModule.operationOrNull()
                .refundBuffer()
                .get("minecraft:gold_ingot:0"));
        assertTrue(
            clientModule.operationOrNull()
                .plan()
                .spec() instanceof ModuleDeconstructionOperation);
    }

    @Test
    void fullSyncRoundTripPreservesModuleRotation() {
        AutomatedFacility server = createFacility();
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.HAMMER,
            StationTileCoord.of(5, 5),
            ModuleShape.QUAD_2x2,
            ModuleTier.IV);
        module.setRotation(1);
        server.addModule(module);
        server.stationLayout()
            .place(module);

        AutomatedFacility client = createFacility();
        applyFullSyncFromPacket(client, roundTrip(AssetSyncPacket.fullSync(server)));

        ModuleInstance clientModule = client.modules()
            .get(0);
        assertEquals(1, clientModule.rotation());
        assertTrue(
            client.stationLayout()
                .isOccupied(StationTileCoord.of(4, 5)));
    }

    @Test
    void fullSyncRoundTripPreservesMinerBlacklist() {
        AutomatedFacility server = createFacility();
        ModuleInstance miner = buildModule(server, FacilityModuleKind.MINER, StationTileCoord.of(1, 0));
        server.setMinerOreBlacklisted(miner, "ore:iron", true);

        AutomatedFacility client = createFacility();
        applyFullSyncFromPacket(client, roundTrip(AssetSyncPacket.fullSync(server)));

        assertTrue(
            client.isMinerOreBlacklisted(
                client.modules()
                    .get(0),
                "ore:iron"));
        assertFalse(
            client.settingsGroups()
                .require(
                    client.modules()
                        .get(0)
                        .groupId())
                .isJoinable());
    }

    @Test
    void fullSyncRoundTripPreservesMinerSettingsGroup() {
        AutomatedFacility server = createFacility();
        ModuleInstance miner = buildModule(server, FacilityModuleKind.MINER, StationTileCoord.of(1, 0));
        server.setMinerOreBlacklisted(miner, "ore:iron", true);
        short groupId = server.createSettingsGroupForModule(miner, "Shared miners")
            .id();

        AutomatedFacility client = createFacility();
        applyFullSyncFromPacket(client, roundTrip(AssetSyncPacket.fullSync(server)));

        ModuleInstance clientMiner = client.modules()
            .get(0);
        assertEquals(groupId, clientMiner.groupId());
        assertEquals(
            "Shared miners",
            client.settingsGroups()
                .require(groupId)
                .displayName());
        assertTrue(
            client.settingsGroups()
                .require(groupId)
                .isJoinable());
        assertTrue(client.isMinerOreBlacklisted(clientMiner, "ore:iron"));
    }

    @Test
    void fullSyncRoundTripPreservesRecipeSnapshotPayload() {
        AutomatedFacility server = createFacility();
        ModuleInstance centrifuge = buildModule(server, FacilityModuleKind.CENTRIFUGE, StationTileCoord.of(1, 0));
        Item inputItem = Items.diamond;
        Item outputItem = Items.diamond;
        RecipeSnapshot snapshot = RecipeSnapshot.resolved(
            (byte) 1,
            832,
            new ItemStack[] { new ItemStack(inputItem, 2, 0) },
            new ItemStack[] { new ItemStack(outputItem, 3, 0) },
            null,
            null,
            200,
            480);
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(new SavedRecipe(snapshot, true, 0L, (byte) 1, (byte) 1));
        ((IRecipeModule) centrifuge.component()).setRecipeConfig(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));

        AutomatedFacility client = createFacility();
        applyFullSyncFromPacket(client, roundTrip(AssetSyncPacket.fullSync(server)));

        RecipeSnapshot clientSnapshot = ((IRecipeModule) client.modules()
            .get(0)
            .component()).getRecipeConfig()
                .savedRecipes()
                .get(0)
                .recipe();
        assertEquals(200, clientSnapshot.duration());
        assertEquals(480, clientSnapshot.eut());
        assertEquals(1, clientSnapshot.inputs().length);
        assertEquals(1, clientSnapshot.outputs().length);
    }

    @Test
    void fullSyncPreservesInventoryBounds() {
        AutomatedFacility server = createFacility();
        ItemStackWrapper iron = ItemStackWrapper.of(new ItemStack(Items.iron_ingot, 1, 0));
        ItemStackWrapper gold = ItemStackWrapper.of(new ItemStack(Items.gold_ingot, 1, 0));
        FluidKey water = FluidKey.of(new FluidStack(FluidRegistry.WATER, 1));
        FluidKey lava = FluidKey.of(new FluidStack(FluidRegistry.LAVA, 1));
        server.setBound(iron, 12L, true);
        server.setBound(iron, 64L, false);
        server.setBound(gold, 8L, true);
        server.setBound(gold, 40L, false);
        server.setBound(water, 1_000L, true);
        server.setBound(water, 16_000L, false);
        server.setBound(lava, 500L, true);
        server.setBound(lava, 4_000L, false);

        AssetSyncPacket full = AssetSyncPacket.fullSync(server);

        AutomatedFacility client = createFacility();
        applyFullSyncFromPacket(client, roundTrip(full));

        assertEquals(
            new InventoryBounds(12L, 64L),
            client.getBounds(true)
                .get(iron));
        assertEquals(
            new InventoryBounds(8L, 40L),
            client.getBounds(true)
                .get(gold));
        assertEquals(
            new InventoryBounds(1_000L, 16_000L),
            client.getBounds(false)
                .get(water));
        assertEquals(
            new InventoryBounds(500L, 4_000L),
            client.getBounds(false)
                .get(lava));
    }

    @Test
    void fullSyncRestoresStoredItemsThatCurrentFilterRejects() {
        AutomatedFacility server = createFacility();
        ItemStackWrapper stored = ItemStackWrapper.of(new ItemStack(Items.stick));
        server.loadFromSnapshot(Map.of(stored, 4L));
        server.setFilters(
            List.of(
                ItemStackWrapper.of(new ItemStack(Items.diamond))
                    .toItemStack()
                    .getUnlocalizedName()),
            true);

        AutomatedFacility client = new AutomatedFacility(
            server.assetId,
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.CLIENT.registerAssetInternal(TEAM, client);

        AssetStateSync.Client.handleFull(roundTrip(AssetSyncPacket.fullSync(server)));

        assertSame(client, CelestialAssetStore.CLIENT.findAssetInternal(server.assetId));
        assertEquals(
            4L,
            client.itemSnapshot()
                .get(stored));
    }

    @Test
    void fullSyncClearsFiltersMissingFromAuthoritativeState() {
        AutomatedFacility server = createFacility();
        AutomatedFacility client = new AutomatedFacility(
            server.assetId,
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        client.setFilters(
            List.of(
                ItemStackWrapper.of(new ItemStack(Items.diamond))
                    .toItemStack()
                    .getUnlocalizedName()),
            true);
        client.setFilters(List.of(FluidRegistry.LAVA.getName()), false);
        CelestialAssetStore.CLIENT.registerAssetInternal(TEAM, client);

        assertTrue(AssetStateSync.Client.handleFull(roundTrip(AssetSyncPacket.fullSync(server))));

        assertSame(client, CelestialAssetStore.CLIENT.findAssetInternal(server.assetId));
        assertEquals(Map.of(), client.filtersSnapshot());
    }

    @Test
    void malformedFacilityFluidPayloadsAreRejectedAtPacketBoundary() {
        AutomatedFacility server = createFacility();
        String water = FluidRegistry.WATER.getName();
        List<List<Map.Entry<String, Long>>> malformedEntries = List.of(
            List.of(Map.entry(" ", 1L)),
            List.of(Map.entry("definitely_unregistered_galaxia_fluid", 1L)),
            List.of(Map.entry(water, 1L), Map.entry(water, 2L)),
            List.of(Map.entry(water, 0L)));

        for (List<Map.Entry<String, Long>> entries : malformedEntries) {
            assertThrows(
                IllegalStateException.class,
                () -> decodeFacilityPacketWithRawFluids(server, entries.size(), entries),
                entries.toString());
        }
        assertThrows(
            IllegalStateException.class,
            () -> decodeFacilityPacketWithRawFluids(server, 4_097, List.of()),
            "fluid entry count above the wire limit must be rejected");
    }

    @Test
    void invalidFullSyncHasNoPartialEffectsOnExistingClientAsset() {
        AutomatedFacility server = createFacility();
        ItemStackWrapper incoming = ItemStackWrapper.of(new ItemStack(Items.diamond));
        server.loadFromSnapshot(Map.of(incoming, 4L));

        AutomatedFacility client = new AutomatedFacility(
            server.assetId,
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ItemStackWrapper existing = ItemStackWrapper.of(new ItemStack(Items.stick));
        client.loadFromSnapshot(Map.of(existing, 7L));
        CelestialAssetStore.CLIENT.registerAssetInternal(TEAM, client);

        AssetSyncPacket invalid = AssetSyncPacket.fullSync(server);
        invalid.fullSyncDeltas()
            .add(AssetSyncPacket.moduleAdded(server.assetId, 0, null));

        assertDoesNotThrow(() -> AssetStateSync.Client.handleFull(invalid));
        assertSame(client, CelestialAssetStore.CLIENT.findAssetInternal(server.assetId));
        assertEquals(Map.of(existing, 7L), client.itemSnapshot());
    }

    @Test
    void debugDataGeneratorStateDeltaUpdatesClientModule() {
        AutomatedFacility source = createFacility(CelestialObjectId.MARS);
        AutomatedFacility destination = createFacility(CelestialObjectId.EGORA);
        ModuleDebugDataGenerator producer = debugDataGenerator(source, StationTileCoord.of(1, 0));
        ModuleDebugDataGenerator consumer = debugDataGenerator(destination, StationTileCoord.of(1, 0));
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.COMMUNICATION, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.COMMUNICATION, 10L, 1, null));
        SatelliteNetworkService.refreshFacilityEndpoints(source);
        SatelliteNetworkService.refreshFacilityEndpoints(destination);
        CelestialAssetStore.SERVER.setSatelliteCount(
            TEAM,
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            SatelliteKind.COMMUNICATION,
            1);
        CelestialAssetStore.SERVER.setSatelliteCount(
            TEAM,
            CelestialObjectKey.registered(CelestialObjectId.EGORA),
            SatelliteKind.COMMUNICATION,
            1);
        SatelliteNetworkService.rebuild(TEAM, 0.0D);

        AutomatedFacility clientSource = createUnregisteredFacility(CelestialObjectId.MARS);
        applyFullSyncFromPacket(clientSource, roundTrip(AssetSyncPacket.fullSync(source)));
        AutomatedFacility clientDestination = createUnregisteredFacility(CelestialObjectId.EGORA);
        applyFullSyncFromPacket(clientDestination, roundTrip(AssetSyncPacket.fullSync(destination)));

        SatelliteNetworkService.tickDataJobs();
        applyFullSyncFromPacket(clientSource, roundTrip(AssetSyncPacket.fullSync(source)));
        applyFullSyncFromPacket(clientDestination, roundTrip(AssetSyncPacket.fullSync(destination)));

        ModuleDebugDataGenerator clientProducer = (ModuleDebugDataGenerator) clientSource.modules()
            .get(0)
            .component();
        ModuleDebugDataGenerator clientConsumer = (ModuleDebugDataGenerator) clientDestination.modules()
            .get(0)
            .component();
        assertEquals(
            CelestialObjectKey.registered(CelestialObjectId.EGORA),
            clientProducer.detectedCounterpartBodyKey());
        assertEquals(
            SatelliteDataType.COMMUNICATION,
            clientConsumer.config()
                .dataType());
        assertEquals(5L, clientConsumer.consumedDeciKb());

        SatelliteNetworkService.tickDataJobs();
        applyFullSyncFromPacket(clientDestination, roundTrip(AssetSyncPacket.fullSync(destination)));

        clientConsumer = (ModuleDebugDataGenerator) clientDestination.modules()
            .get(0)
            .component();
        assertEquals(10L, clientConsumer.consumedDeciKb());
    }

    private static AssetSyncPacket roundTrip(AssetSyncPacket pkt) {
        var buf = Unpooled.buffer();
        pkt.toBytes(buf);
        AssetSyncPacket decoded = new AssetSyncPacket();
        decoded.fromBytes(buf);
        return decoded;
    }

    private static AssetSyncPacket decodeFacilityPacketWithRawFluids(AutomatedFacility facility, int declaredCount,
        List<Map.Entry<String, Long>> entries) {
        AssetSyncPacket full = AssetSyncPacket.fullSync(facility);
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeByte(full.syncType);
            buf.writeInt(full.stateRevision);
            buf.writeLong(full.basePublishedRevision);
            buf.writeLong(full.publishedRevision);
            PacketUtil.writeId(buf, full.assetId);
            PacketUtil.writeEnum(buf, full.assetKind);
            PacketUtil.writeEnum(buf, full.assetStatus);
            PacketUtil.writeString(buf, full.displayName == null ? "" : full.displayName);
            buf.writeLong(full.teamId.getMostSignificantBits());
            buf.writeLong(full.teamId.getLeastSignificantBits());
            PacketUtil.writeCelestialObjectKey(buf, full.celestialBodyKey);
            PacketUtil.writeCelestialObjectKey(buf, full.systemKey);
            PacketUtil.writeCelestialObjectKey(buf, full.planetaryAnchorBodyKey);
            buf.writeLong(full.energyStored);
            buf.writeLong(full.stationFeatureSalt);
            buf.writeInt(0);
            buf.writeInt(0);
            buf.writeInt(declaredCount);
            for (Map.Entry<String, Long> entry : entries) {
                PacketUtil.writeString(buf, entry.getKey());
                buf.writeLong(entry.getValue());
            }
            buf.writeInt(0);

            AssetSyncPacket decoded = new AssetSyncPacket();
            decoded.fromBytes(buf);
            return decoded;
        } finally {
            buf.release();
        }
    }

    private static AutomatedFacility createFacility() {
        return createFacility(CelestialObjectId.MARS);
    }

    private static AutomatedFacility createFacility(CelestialObjectId bodyId) {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            bodyId,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        return facility;
    }

    private static AutomatedFacility createUnregisteredFacility(CelestialObjectId bodyId) {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            bodyId,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static AutomatedFacility facilityWithRecipeConfig(RecipeSnapshot... snapshots) {
        AutomatedFacility facility = createFacility();
        ModuleInstance module = buildModule(facility, FacilityModuleKind.CENTRIFUGE, StationTileCoord.of(1, 0));
        SavedRecipeList slots = new SavedRecipeList();
        for (RecipeSnapshot snapshot : snapshots) {
            slots.add(new SavedRecipe(snapshot, true, 0L, (byte) 1, (byte) 1));
        }
        ((IRecipeModule) module.component()).setRecipeConfig(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));
        return facility;
    }

    private static RecipeSnapshot recipeSnapshot(int recipeIndex) {
        Item item = Items.diamond;
        return RecipeSnapshot.resolved(
            (byte) 1,
            recipeIndex,
            new ItemStack[] { new ItemStack(item, 2, 0) },
            new ItemStack[] { new ItemStack(item, 3, 0) },
            null,
            null,
            200,
            480);
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
        for (StationTileCoord coord : module.tiles()) {
            layout.place(coord, new PlacedTile(module, state));
        }
        return module;
    }

    private static ModuleDebugDataGenerator debugDataGenerator(AutomatedFacility facility, StationTileCoord anchor) {
        ModuleInstance module = buildModule(facility, FacilityModuleKind.DEBUG_DATA_GENERATOR, anchor);
        module.completeConstruction();
        return (ModuleDebugDataGenerator) module.component();
    }

    private static void applyFullSyncFromPacket(AutomatedFacility client, AssetSyncPacket packet) {
        client.clearModules();
        client.settingsGroups()
            .clear();
        client.clear();
        client.logisticsConfig.clear();
        StationLayout layout = client.stationLayout();
        if (layout != null) layout.loadFromSnapshot(java.util.Collections.emptyMap());

        for (AssetSyncPacket d : packet.fullSyncDeltas()) {
            AssetStateSync.Client.handleDelta(client, d);
        }
        client.setStateRevision(packet.stateRevision());
    }
}
