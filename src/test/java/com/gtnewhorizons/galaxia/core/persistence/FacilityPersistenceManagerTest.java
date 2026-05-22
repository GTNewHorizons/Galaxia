package com.gtnewhorizons.galaxia.core.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.gtnewhorizons.galaxia.core.network.PacketUtil;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
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
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class FacilityPersistenceManagerTest {

    private static final Gson GSON = new Gson();
    private static final Gson PERSISTENCE_GSON = new GsonBuilder().serializeNulls()
        .create();

    private static Fluid TEST_FLUID_1;
    private static Fluid TEST_FLUID_2;

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
        TEST_FLUID_1 = FluidRegistry.WATER;
        TEST_FLUID_2 = FluidRegistry.LAVA;
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

    @Test
    void hammerVariantRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = createStationWithFullLayout();
        ModuleHammer hammer = (ModuleHammer) station.modules()
            .get(0)
            .component();
        station.modules()
            .get(0)
            .setTier(ModuleTier.LuV);
        hammer.setVariant(HammerVariant.BIG);

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        assertEquals(
            "BIG",
            encoded.modules.get(0).data.getAsJsonObject()
                .get("variant")
                .getAsString());

        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        ModuleHammer decodedHammer = (ModuleHammer) decoded.modules()
            .get(0)
            .component();
        assertEquals(HammerVariant.BIG, decodedHammer.variant());
    }

    @Test
    void minerBlacklistRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance miner = station.modules()
            .get(1);
        station.setMinerOreBlacklisted(miner, "ore:iron", true);

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        assertTrue(
            decoded.isMinerOreBlacklisted(
                decoded.modules()
                    .get(1),
                "ore:iron"));
    }

    @Test
    void minerFocusRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = createStationWithFullLayout();
        ModuleMiner miner = (ModuleMiner) station.modules()
            .get(1)
            .component();
        miner.setFocus(MinerFocusTier.III, "ore:iron", 1200);

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        ModuleMiner decodedMiner = (ModuleMiner) decoded.modules()
            .get(1)
            .component();
        assertEquals(MinerFocusTier.III, decodedMiner.focusTier());
        assertEquals("ore:iron", decodedMiner.focusOreKeyOrNull());
        assertEquals(1200, decodedMiner.focusAlignmentProgress());
    }

    @Test
    void hammerEnergyBufferRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = createStationWithFullLayout();
        ModuleHammer hammer = (ModuleHammer) station.modules()
            .get(0)
            .component();
        hammer.setEnergyStored(234_567L);

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        ModuleHammer decodedHammer = (ModuleHammer) decoded.modules()
            .get(0)
            .component();
        assertEquals(234_567L, decodedHammer.energyStored());
    }

    @Test
    void hammerDispatchCooldownsRoundTripThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance module = station.modules()
            .get(0);
        module.setTier(ModuleTier.IV);
        ModuleHammer hammer = (ModuleHammer) module.component();
        hammer.markShotDispatched(module);
        hammer.markRouteProbeAttempted();

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        ModuleHammer decodedHammer = (ModuleHammer) decoded.modules()
            .get(0)
            .component();
        assertEquals(hammer.shotCooldownTicks(), decodedHammer.shotCooldownTicks());
        assertEquals(hammer.routeProbeCooldownTicks(), decodedHammer.routeProbeCooldownTicks());
    }

    @Test
    void minerFocusTierWithoutOreRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = createStationWithFullLayout();
        ModuleMiner miner = (ModuleMiner) station.modules()
            .get(1)
            .component();
        miner.setFocus(MinerFocusTier.II, null, 1200);

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        ModuleMiner decodedMiner = (ModuleMiner) decoded.modules()
            .get(1)
            .component();
        assertEquals(MinerFocusTier.II, decodedMiner.focusTier());
        assertNull(decodedMiner.focusOreKeyOrNull());
        assertEquals(0, decodedMiner.focusAlignmentProgress());
    }

    @Test
    void minerSettingsGroupRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance miner = station.modules()
            .get(1);
        station.setMinerOreBlacklisted(miner, "ore:iron", true);
        short groupId = station.createSettingsGroupForModule(miner, "Shared miners")
            .id();

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        JsonObject encodedState = PERSISTENCE_GSON.toJsonTree(encoded)
            .getAsJsonObject();
        JsonObject encodedMinerData = null;
        com.google.gson.JsonArray modules = encodedState.getAsJsonArray("modules");
        for (int i = 0; i < modules.size(); i++) {
            JsonObject moduleJson = modules.get(i)
                .getAsJsonObject();
            if (miner.id.toString()
                .equals(
                    moduleJson.get("moduleId")
                        .getAsString())) {
                encodedMinerData = moduleJson.getAsJsonObject("data");
                break;
            }
        }
        assertNotNull(encodedMinerData);
        assertFalse(encodedMinerData.has("localSettings"));
        assertTrue(encodedMinerData.has("focusOreKey"));
        assertTrue(
            encodedMinerData.get("focusOreKey")
                .isJsonNull());

        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        ModuleInstance decodedMiner = decoded.modules()
            .get(1);
        assertEquals(groupId, decodedMiner.groupId());
        assertTrue(decoded.isMinerOreBlacklisted(decodedMiner, "ore:iron"));
        assertEquals(
            "Shared miners",
            decoded.settingsGroups()
                .require(groupId)
                .displayName());
        assertTrue(
            decoded.settingsGroups()
                .require(groupId)
                .isJoinable());
    }

    @Test
    void moduleOperationRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance hammer = station.modules()
            .get(0);
        ModuleOperationState operation = ModuleOperationState
            .waiting(hammerOperationPlan(hammer, ModuleTier.LuV, HammerVariant.BIG, true, true))
            .withDepositedResources(Map.of("minecraft:iron_ingot:0", 8L))
            .beginBuilding()
            .tickBuilding();
        hammer.setOperation(operation);

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        ModuleOperationState decodedOperation = decoded.modules()
            .get(0)
            .operationOrNull();
        assertNotNull(decodedOperation);
        assertEquals(ModuleOperationPhase.BUILDING, decodedOperation.phase());
        assertEquals(1, decodedOperation.elapsedBuildTicks());
        assertTrue(decodedOperation.reserveItems());
        assertTrue(
            decodedOperation.plan()
                .voidCompletionRefund());
        assertTrue(
            decodedOperation.plan()
                .spec() instanceof HammerModuleOperation);
        assertEquals(
            "BIG",
            ((HammerModuleOperation) decodedOperation.plan()
                .spec()).targetVariantKey());
        assertEquals(
            ModuleTier.LuV,
            decodedOperation.plan()
                .spec()
                .targetTier());
        assertEquals(
            8L,
            decodedOperation.depositedResources()
                .get("minecraft:iron_ingot:0"));
    }

    @Test
    void moduleOperationRoundTripPreservesPlannedBuildTicks() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance hammer = station.modules()
            .get(0);
        hammer.setOperation(
            ModuleOperationState
                .waiting(
                    new ModuleOperationPlan(
                        new HammerModuleOperation(ModuleTier.LuV, HammerVariant.BIG.name()),
                        37,
                        Map.of(),
                        false))
                .beginBuilding()
                .tickBuilding());

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        ModuleOperationState decodedOperation = decoded.modules()
            .get(0)
            .operationOrNull();
        assertNotNull(decodedOperation);
        assertEquals(
            37,
            decodedOperation.plan()
                .buildTicks());
    }

    @Test
    void moduleTierOperationRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance module = station.modules()
            .get(1);
        module.setOperation(
            ModuleOperationState
                .waiting(new ModuleOperationPlan(new ModuleTierOperation(ModuleTier.IV), 37, Map.of(), false)));

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        ModuleOperationState decodedOperation = decoded.modules()
            .get(1)
            .operationOrNull();
        assertNotNull(decodedOperation);
        assertTrue(
            decodedOperation.plan()
                .spec() instanceof ModuleTierOperation);
        assertEquals(
            ModuleTier.IV,
            decodedOperation.plan()
                .spec()
                .targetTier());
    }

    @Test
    void malformedModuleOperationCrashesOnLoad() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance hammer = station.modules()
            .get(0);
        hammer.setOperation(
            ModuleOperationState.waiting(hammerOperationPlan(hammer, ModuleTier.IV, HammerVariant.BASE, false, false)));
        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        encoded.modules.get(0).moduleOperation.phase = "BROKEN";

        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());

        assertThrows(IllegalStateException.class, () -> manager.decodeFacilityState(decoded, encoded));
    }

    @Test
    void obsoleteMinerBlacklistDataCrashesOnLoad() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = createStationWithFullLayout();
        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        encoded.modules.get(1).data.getAsJsonObject()
            .addProperty("blacklistedItemKeys", "ore:iron");

        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());

        assertThrows(IllegalStateException.class, () -> manager.decodeFacilityState(decoded, encoded));
    }

    @Test
    void malformedAssetFileCrashesInsteadOfSkippingAsset(@TempDir Path tempDir) throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        UUID teamId = UUID.randomUUID();

        FacilityPersistenceManager.AssetJson station = assetJson(
            teamId,
            CelestialAsset.Kind.STATION,
            CelestialObjectId.MOON);
        FacilityPersistenceManager.AssetJson outpost = assetJson(
            teamId,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            CelestialObjectId.PANSPIRA);
        outpost.facility = malformedFacilityState();

        List<FacilityPersistenceManager.AssetJson> assets = new ArrayList<>();
        assets.add(station);
        assets.add(outpost);

        File file = tempDir.resolve("_assets.json")
            .toFile();
        Files.write(
            file.toPath(),
            GSON.toJson(assets)
                .getBytes(StandardCharsets.UTF_8));

        CelestialAssetStore.clear();
        Method loadAssets = FacilityPersistenceManager.class.getDeclaredMethod("loadAssets", File.class);
        loadAssets.setAccessible(true);

        InvocationTargetException thrown = assertThrows(
            InvocationTargetException.class,
            () -> loadAssets.invoke(manager, file));
        assertTrue(thrown.getCause() instanceof IllegalStateException);
        assertTrue(
            thrown.getCause()
                .getMessage()
                .contains("malformed"));
    }

    private static FacilityPersistenceManager.AssetJson assetJson(UUID teamId, CelestialAsset.Kind kind,
        CelestialObjectId body) {
        FacilityPersistenceManager.AssetJson json = new FacilityPersistenceManager.AssetJson();
        json.teamId = teamId.toString();
        json.assetId = CelestialAsset.ID.create();
        json.celestialObjectId = body.toString();
        json.displayName = body + ":" + kind;
        json.kind = kind.name();
        json.location = CelestialAsset.Location.ofKind(kind)
            .name();
        json.status = Buildable.Status.OPERATIONAL.name();
        json.requiredResources = new LinkedHashMap<>();
        json.constructionInventory = new LinkedHashMap<>();
        return json;
    }

    private static FacilityPersistenceManager.FacilityStateJson malformedFacilityState() {
        FacilityPersistenceManager.FacilityStateJson facility = new FacilityPersistenceManager.FacilityStateJson();
        facility.settingsGroupsNextId = 1;
        facility.settingsGroups = new ArrayList<>();
        facility.modules = new ArrayList<>();
        facility.buffer = new LinkedHashMap<>();
        facility.fluidBuffer = new LinkedHashMap<>();
        facility.layoutTiles = new ArrayList<>();

        FacilityPersistenceManager.ModuleJson miner = new FacilityPersistenceManager.ModuleJson();
        miner.moduleId = ModuleInstance.ID.create()
            .toString();
        miner.kind = FacilityModuleKind.MINER.name();
        miner.status = Buildable.Status.OPERATIONAL.name();
        miner.tier = PacketUtil.enumOrdinal(ModuleTier.EV);
        miner.shape = PacketUtil.enumOrdinal(ModuleShape.SINGLE);
        miner.priorityOverride = PacketUtil.enumOrdinal(ModulePriority.NORMAL);
        miner.enabled = true;
        miner.parallel = 1;
        JsonObject minerData = new JsonObject();
        JsonObject localSettings = new JsonObject();
        localSettings.add("blacklistedOreKeys", GSON.toJsonTree(new ArrayList<String>()));
        minerData.add("localSettings", localSettings);
        miner.data = minerData;
        facility.modules.add(miner);
        return facility;
    }

    private static AutomatedFacility createStationWithFullLayout() {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        station.setEnergyStored(245_760L);

        StationLayout layout = station.stationLayout();
        assertNotNull(layout);

        ModuleInstance hammer = addModule(
            station,
            FacilityModuleKind.HAMMER,
            Buildable.Status.OPERATIONAL,
            StationTileCoord.of(1, 0));
        hammer.initAnchor(StationTileCoord.of(1, 0));
        layout.place(hammer);

        ModuleInstance miner = addModule(
            station,
            FacilityModuleKind.MINER,
            Buildable.Status.DISABLED,
            StationTileCoord.of(2, 0));
        miner.initAnchor(StationTileCoord.of(2, 0));
        layout.place(miner);

        ModuleInstance power = addModule(
            station,
            FacilityModuleKind.POWER,
            Buildable.Status.IN_CONSTRUCTION,
            StationTileCoord.of(2, 1));
        power.initAnchor(StationTileCoord.of(2, 1));
        layout.place(power);
        return station;
    }

    private static ModuleInstance addModule(AutomatedFacility station, FacilityModuleKind kind,
        Buildable.Status status) {
        return addModule(station, kind, status, null);
    }

    private static ModuleInstance addModule(AutomatedFacility station, FacilityModuleKind kind, Buildable.Status status,
        StationTileCoord anchor) {
        ModuleInstance module = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), kind, anchor, ModuleShape.SINGLE, kind.defaultTier());
        module.updateStatus(status);
        station.addModule(module);
        return module;
    }

    private static ModuleOperationPlan hammerOperationPlan(ModuleInstance module, ModuleTier targetTier,
        HammerVariant targetVariant, boolean reserveItems, boolean voidCompletionRefund) {
        int buildTicks = FacilityModuleRegistry.get(module.kind())
            .getTierData(module.tier())
            .buildTicks();
        Map<ItemStackWrapper, Long> cost = FacilityModuleRegistry.operationCost(
            FacilityModuleRegistry.get(module.kind())
                .getTierData(targetTier)
                .constructionCost());
        return new ModuleOperationPlan(
            new HammerModuleOperation(targetTier, targetVariant.name()),
            buildTicks,
            cost,
            reserveItems,
            voidCompletionRefund);
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

        AutomatedFacility malformedTier = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        assertThrows(IllegalStateException.class, () -> manager.decodeFacilityState(malformedTier, encoded));
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
    void fluidBufferRoundTripsThroughFacilityPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        FluidKey bufferKey = new FluidKey(TEST_FLUID_1, null);
        station.updateFluids(bufferKey, 4096);

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());
        manager.decodeFacilityState(decoded, encoded);

        assertEquals(4096, decoded.getFluidAmount(bufferKey));
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

        // Create ALL module kinds with various statuses
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
        createAndPlaceModule(
            station,
            FacilityModuleKind.GEOTHERMAL_GENERATOR,
            Buildable.Status.OPERATIONAL,
            ModuleShape.BLOCK_3x3,
            ModuleTier.HV,
            StationTileCoord.of(5, 0));
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
        ModuleInstance macerator = createAndPlaceModule(
            station,
            FacilityModuleKind.MACERATOR,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.HV,
            StationTileCoord.of(2, 2));
        createAndPlaceModule(
            station,
            FacilityModuleKind.CENTRIFUGE,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.HV,
            StationTileCoord.of(3, 2));
        createAndPlaceModule(
            station,
            FacilityModuleKind.ELECTROLYZER,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.HV,
            StationTileCoord.of(1, 3));
        createAndPlaceModule(
            station,
            FacilityModuleKind.CHEMICAL_REACTOR,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.HV,
            StationTileCoord.of(2, 3));
        createAndPlaceModule(
            station,
            FacilityModuleKind.ASSEMBLER,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.HV,
            StationTileCoord.of(3, 3));
        createAndPlaceModule(
            station,
            FacilityModuleKind.DISTILLERY,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.HV,
            StationTileCoord.of(1, 4));

        StationLayout layout = station.stationLayout();
        assertNotNull(layout);

        // Encode to JSON
        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);

        // Dump JSON for inspection
        String encodedJson = FacilityPersistenceManagerTest.GSON.toJson(encoded);
        System.out.println("=== Encoded FacilityStateJson (all kinds) ===");
        System.out.println(encodedJson);
        System.out.println("=== End encoded JSON ===");
        System.out.println("Module count: " + encoded.modules.size());
        System.out.println("Layout tile count: " + encoded.layoutTiles.size());

        // Verify module entries
        assertEquals(14, encoded.modules.size());
        assertEquals(14, encoded.layoutTiles.size());

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
                .anyMatch(mj -> "GEOTHERMAL_GENERATOR".equals(mj.kind)));
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
        assertTrue(
            encoded.modules.stream()
                .anyMatch(mj -> "MACERATOR".equals(mj.kind)));
        assertTrue(
            encoded.modules.stream()
                .anyMatch(mj -> "CENTRIFUGE".equals(mj.kind)));
        assertTrue(
            encoded.modules.stream()
                .anyMatch(mj -> "ELECTROLYZER".equals(mj.kind)));
        assertTrue(
            encoded.modules.stream()
                .anyMatch(mj -> "CHEMICAL_REACTOR".equals(mj.kind)));
        assertTrue(
            encoded.modules.stream()
                .anyMatch(mj -> "ASSEMBLER".equals(mj.kind)));
        assertTrue(
            encoded.modules.stream()
                .anyMatch(mj -> "DISTILLERY".equals(mj.kind)));

        // Verify shape bytes — SINGLE has ordinal 0
        for (FacilityPersistenceManager.ModuleJson mj : encoded.modules) {
            int expectedShape = "GEOTHERMAL_GENERATOR".equals(mj.kind) ? ModuleShape.BLOCK_3x3.ordinal()
                : ModuleShape.SINGLE.ordinal();
            assertEquals(expectedShape, mj.shape, "Unexpected shape for " + mj.kind);
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
                14,
                decoded.modules()
                    .size(),
                "Expected 14 modules, got " + decoded.modules()
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
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(5, 0), "GEOTHERMAL_GENERATOR anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(4, -1), "GEOTHERMAL_GENERATOR child"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(6, 1), "GEOTHERMAL_GENERATOR child"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(1, 1), "STORAGE anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(2, 1), "TANK anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(3, 1), "BATTERY anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(1, 2), "MAINTENANCE_BAY anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(2, 2), "MACERATOR anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(3, 2), "CENTRIFUGE anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(1, 3), "ELECTROLYZER anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(2, 3), "CHEMICAL_REACTOR anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(3, 3), "ASSEMBLER anchor"),
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(1, 4), "DISTILLERY anchor"),
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

    @Test
    void savedRecipesnapshotsRoundTripFluidStacksAndRecipeStats() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        ModuleInstance macerator = createAndPlaceModule(
            station,
            FacilityModuleKind.MACERATOR,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.HV,
            StationTileCoord.of(2, 2));
        IRecipeModule recipeModule = (IRecipeModule) macerator.component();
        FluidStack[] fluidInputs = { new FluidStack(TEST_FLUID_1, 144) };
        FluidStack[] fluidOutputs = { new FluidStack(TEST_FLUID_2, 72) };
        int[] outputChances = { 5000 };
        int[] fluidOutputChances = { 7500 };
        long contentHash = RecipeSnapshot
            .computeContentHash(null, null, fluidInputs, fluidOutputs, outputChances, fluidOutputChances, 320, 480);
        RecipeSnapshot snapshot = new RecipeSnapshot(
            (byte) 1,
            7,
            contentHash,
            null,
            null,
            fluidInputs,
            fluidOutputs,
            outputChances,
            fluidOutputChances,
            320,
            480);
        SavedRecipeList slots = new SavedRecipeList();
        station.setBound(new FluidKey(TEST_FLUID_1, null), 11, true);
        station.setBound(new FluidKey(TEST_FLUID_2, null), 22, false);
        slots.add(new SavedRecipe(snapshot, true, 12L, (byte) 3, (byte) 4));
        recipeModule.setRecipeConfig(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));

        FacilityPersistenceManager.AssetJson aencoded = manager.encodeAsset(station);
        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        AutomatedFacility decoded = (AutomatedFacility) manager.decodeAsset(aencoded);
        manager.decodeFacilityState(decoded, encoded);

        ModuleInstance decodedMacerator = decoded.modules()
            .stream()
            .filter(m -> m.kind() == FacilityModuleKind.MACERATOR)
            .findFirst()
            .orElseThrow();
        RecipeConfig decodedConfig = ((IRecipeModule) decodedMacerator.component()).getRecipeConfig();
        assertNotNull(decodedConfig);
        SavedRecipe decodedSlot = decodedConfig.savedRecipes()
            .get(0);
        RecipeSnapshot decodedSnapshot = decodedSlot.recipe();
        assertEquals(320, decodedSnapshot.duration());
        assertEquals(480, decodedSnapshot.eut());
        assertEquals(contentHash, decodedSnapshot.contentHash());
        assertEquals(5000, decodedSnapshot.outputChances()[0]);
        assertEquals(7500, decodedSnapshot.fluidOutputChances()[0]);
        assertEquals(
            new FluidKey(TEST_FLUID_1, null).fluid()
                .getName(),
            fluidName(decodedSnapshot.fluidInputs()[0]));
        assertEquals(144, decodedSnapshot.fluidInputs()[0].amount);
        assertEquals(
            new FluidKey(TEST_FLUID_2, null).fluid()
                .getName(),
            fluidName(decodedSnapshot.fluidOutputs()[0]));
        assertEquals(72, decodedSnapshot.fluidOutputs()[0].amount);
        assertEquals(12L, decodedSlot.requestAmount());
        assertEquals(
            11,
            decoded.getBound(new FluidKey(TEST_FLUID_1, null))
                .lowOrDefault());
        assertEquals(
            22,
            decoded.getBound(new FluidKey(TEST_FLUID_2, null))
                .upperOrDefault());
        assertEquals(3, decodedSlot.priority());
        assertEquals(4, decodedSlot.orderSize());
    }

    // ── Helpers ──

    private static ModuleInstance createAndPlaceModule(AutomatedFacility station, FacilityModuleKind kind,
        Buildable.Status status, ModuleShape shape, ModuleTier tier, StationTileCoord coord) {
        ModuleInstance module = FacilityModuleRegistry.create(ModuleInstance.ID.create(), kind, null, shape, tier);
        module.updateStatus(status);
        module.initAnchor(coord);
        station.addModule(module);
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

    private static String fluidName(FluidStack stack) {
        return stack.getFluid()
            .getName();
    }

    @Test
    void unknownModuleKindCrashesOnLoad() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();

        // Simulate a save with a module that has an unresolvable kind (unknown enum value)
        FacilityPersistenceManager.FacilityStateJson legacy = new FacilityPersistenceManager.FacilityStateJson();
        legacy.energyStored = 0L;
        legacy.settingsGroupsNextId = 1;
        legacy.settingsGroups = new ArrayList<>();
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
        legacy.modules.clear();
        legacy.modules.add(unknownMj);
        legacy.modules.add(hammerMj);

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
        AutomatedFacility decoded = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        assertThrows(IllegalStateException.class, () -> manager.decodeFacilityState(decoded, legacy));
    }

    @Test
    void fullPersistenceRoundTripValidatesEveryModuleAndTile() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility before = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        // Create ALL module kinds with both single-tile and multi-tile placements,
        // arranged in rows to stay within StationTileCoord range [-31, 31].
        int rowY = 5;
        int colX = -30;
        for (FacilityModuleKind kind : FacilityModuleKind.values()) {
            ModuleShape shape = (kind.ordinal() % 3 == 0) ? ModuleShape.SINGLE
                : (kind.ordinal() % 3 == 1) ? ModuleShape.QUAD_2x2 : ModuleShape.BLOCK_3x3;
            int step = shape == ModuleShape.BLOCK_3x3 ? 6 : (shape == ModuleShape.QUAD_2x2 ? 4 : 3);
            if (colX + step > 31) {
                rowY += 3;
                colX = -30;
            }
            StationTileCoord coord = StationTileCoord.of(colX, rowY);
            ModuleTier tier = kind.defaultTier();
            ModuleInstance m = createAndPlaceModule(before, kind, Buildable.Status.OPERATIONAL, shape, tier, coord);
            assertNotNull(m.anchorOrNull(), "Module " + kind + " must have non-null anchor after placement");
            colX += step;
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
