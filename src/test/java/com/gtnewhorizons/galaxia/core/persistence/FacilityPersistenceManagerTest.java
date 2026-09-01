package com.gtnewhorizons.galaxia.core.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.core.state.AssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanScope;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleDeconstructionOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBookOwner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeScheduleState;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepAmount;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepSettlement;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;
import com.gtnewhorizons.galaxia.testing.TestFluidStacks;

final class FacilityPersistenceManagerTest {

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
        CelestialServerRuntime runtime = CelestialServerRuntime.create();
        FacilityPersistenceManager manager = new FacilityPersistenceManager(runtime);
        AutomatedFacility station = createStationWithFullLayout();

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());

        decoded = decodeFacility(decoded, encoded);

        assertEquals(station.getEnergyStored(), decoded.getEnergyStored());
        assertEquals(
            station.modules()
                .size(),
            decoded.modules()
                .size());
        assertLayoutEquals(station.stationLayout(), decoded.stationLayout());
    }

    @Test
    void assetStateRoundTripPreservesCanonicalAssetAndModuleState() {
        UUID teamId = UUID.randomUUID();
        AutomatedFacility facility = createStationWithFullLayout();
        ModuleInstance hammer = facility.modules()
            .get(0);
        ModuleInstance miner = facility.modules()
            .get(1);

        NBTTagCompound assetItemTag = new NBTTagCompound();
        assetItemTag.setString("owner", "asset");
        ItemStack assetItem = new ItemStack(Items.gold_ingot);
        assetItem.setTagCompound(assetItemTag);
        facility.setConstructionInventory(Map.of(assetItem, 9L));

        NBTTagCompound moduleItemTag = new NBTTagCompound();
        moduleItemTag.setString("owner", "module");
        ItemStack moduleItem = new ItemStack(Items.diamond);
        moduleItem.setTagCompound(moduleItemTag);
        hammer.getConstructionInventory()
            .put(moduleItem, 11L);
        hammer.setTicks(17);
        hammer.setPriorityOverride(ModulePriority.CRITICAL);
        hammer.setEnabled(false);
        ((IParallelModule) hammer.component()).setParallel((byte) 4);
        ModuleHammer hammerComponent = (ModuleHammer) hammer.component();
        hammerComponent.setDispatchCooldowns(23, 29);
        ((ModuleMiner) miner.component()).setFocus(MinerFocusTier.III, "ore:diamond", 31);

        NBTTagCompound itemConfigTag = new NBTTagCompound();
        itemConfigTag.setString("grade", "refined");
        ItemStackWrapper taggedItem = new ItemStackWrapper(Items.iron_ingot, 0, itemConfigTag);
        NBTTagCompound fluidConfigTag = new NBTTagCompound();
        fluidConfigTag.setString("temperature", "cold");
        FluidKey taggedFluid = new FluidKey(TEST_FLUID_1, fluidConfigTag);
        LogisticsResourceConfig itemConfig = new LogisticsResourceConfig(7, 13, true, false);
        LogisticsResourceConfig fluidConfig = new LogisticsResourceConfig(17, 19, false, true);
        facility.logisticsConfig.set(taggedItem, itemConfig);
        facility.logisticsConfig.set(taggedFluid, fluidConfig);

        NBTTagCompound costTag = new NBTTagCompound();
        costTag.setString("source", "persisted-plan");
        ItemStackWrapper actualCostItem = new ItemStackWrapper(Items.emerald, 0, costTag);
        Map<ItemStackWrapper, Long> actualMaterialCost = Map.of(actualCostItem, 73L);
        hammer.setOperation(
            ModuleOperationState.waiting(
                new ModuleOperationPlan(
                    new HammerModuleOperation(ModuleTier.LuV, HammerVariant.BIG.name()),
                    37,
                    actualMaterialCost,
                    true)));

        AssetState.Decoded facilityState = AssetState.decode(AssetState.encode(teamId, facility));
        AutomatedFacility decoded = (AutomatedFacility) facilityState.asset();
        ModuleInstance decodedHammer = decoded.modules()
            .stream()
            .filter(module -> module.id.equals(hammer.id))
            .findFirst()
            .orElseThrow();
        ModuleInstance decodedMiner = decoded.modules()
            .stream()
            .filter(module -> module.id.equals(miner.id))
            .findFirst()
            .orElseThrow();

        assertEquals(teamId, facilityState.teamId());
        assertEquals(
            1,
            decoded.constructionInventory()
                .size());
        assertEquals(
            ItemStackWrapper.of(assetItem),
            ItemStackWrapper.of(
                decoded.constructionInventory()
                    .keySet()
                    .iterator()
                    .next()));
        assertEquals(
            9L,
            decoded.constructionInventory()
                .values()
                .iterator()
                .next());
        assertEquals(
            1,
            decodedHammer.getConstructionInventory()
                .size());
        assertEquals(
            ItemStackWrapper.of(moduleItem),
            ItemStackWrapper.of(
                decodedHammer.getConstructionInventory()
                    .keySet()
                    .iterator()
                    .next()));
        assertEquals(
            11L,
            decodedHammer.getConstructionInventory()
                .values()
                .iterator()
                .next());
        assertEquals(17, decodedHammer.ticks());
        assertEquals(ModulePriority.CRITICAL, decodedHammer.priorityOverride());
        assertFalse(decodedHammer.enabled());
        assertEquals(4, ((IParallelModule) decodedHammer.component()).getParallel());
        ModuleHammer decodedHammerComponent = (ModuleHammer) decodedHammer.component();
        assertEquals(23, decodedHammerComponent.shotCooldownTicks());
        assertEquals(29, decodedHammerComponent.routeProbeCooldownTicks());
        ModuleMiner decodedMinerComponent = (ModuleMiner) decodedMiner.component();
        assertEquals(MinerFocusTier.III, decodedMinerComponent.focusTier());
        assertEquals("ore:diamond", decodedMinerComponent.focusOreKeyOrNull());
        assertEquals(31, decodedMinerComponent.focusAlignmentProgress());
        assertEquals(
            itemConfig,
            decoded.logisticsConfig.snapshot()
                .get(taggedItem));
        assertEquals(
            fluidConfig,
            decoded.logisticsConfig.snapshot()
                .get(taggedFluid));
        assertEquals(
            actualMaterialCost,
            decodedHammer.operationOrNull()
                .plan()
                .materialCost());

        Station station = new Station(
            CelestialAsset.ID.create(),
            CelestialObjectId.MOON,
            Buildable.Status.IN_CONSTRUCTION);
        BlockPos controller = new BlockPos(3, 5, 7);
        station.setController(controller);
        AssetState.Decoded stationState = AssetState.decode(AssetState.encode(teamId, station));
        assertEquals(teamId, stationState.teamId());
        assertEquals(controller, ((Station) stationState.asset()).getController());
        assertEquals(
            Buildable.Status.IN_CONSTRUCTION,
            stationState.asset()
                .status());

        Satellite satellite = new Satellite(
            CelestialAsset.ID.create(),
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            Buildable.Status.OPERATIONAL,
            SatelliteKind.PROSPECTING);
        AssetState.Decoded satelliteState = AssetState.decode(AssetState.encode(teamId, satellite));
        assertEquals(teamId, satelliteState.teamId());
        assertEquals(SatelliteKind.PROSPECTING, ((Satellite) satelliteState.asset()).satelliteKind());
    }

    @Test
    void celestialDiscoveryScansSurviveSaveAndReload(@TempDir Path tempDir) throws Exception {
        UUID teamId = UUID.randomUUID();
        CelestialServerRuntime runtime = CelestialServerRuntime.create();
        CelestialDiscoveryScanSnapshot expected = new CelestialDiscoveryScanSnapshot(
            teamId,
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            2.5,
            7L,
            CelestialDiscoveryCapability.PROSPECTING,
            CelestialDiscoveryScanSnapshot.Status.ACTIVE,
            CelestialObjectKey.registered(CelestialObjectId.MOON),
            CelestialDiscoveryStep.DETECTION,
            3L);
        runtime.scans()
            .restore(teamId, List.of(expected));

        new FacilityPersistenceManager(runtime).saveToSaveDirectory(tempDir.toFile());

        CelestialServerRuntime reloaded = CelestialServerRuntime.create();
        new FacilityPersistenceManager(reloaded).loadFromSaveDirectory(tempDir.toFile());

        assertEquals(
            List.of(expected),
            reloaded.scans()
                .snapshots(teamId));
    }

    @Test
    void missingDiscoveryFileClearsRuntimeOwnedScans(@TempDir Path tempDir) throws Exception {
        CelestialServerRuntime runtime = CelestialServerRuntime.create();
        UUID teamId = UUID.randomUUID();
        runtime.scans()
            .restore(teamId, List.of(activeScan(teamId, CelestialObjectId.MARS)));
        Path dataDir = tempDir.resolve("galaxiadata");
        Files.createDirectories(dataDir);

        new CelestialDiscoveryPersistenceAdapter(runtime.scans()).load(
            dataDir.resolve("_discovery.json")
                .toFile(),
            PERSISTENCE_GSON);

        assertEquals(
            List.of(),
            runtime.scans()
                .snapshots(teamId));
    }

    @Test
    void missingGalaxiaDataDirectoryClearsRuntimeOwnedScans(@TempDir Path tempDir) {
        CelestialServerRuntime runtime = CelestialServerRuntime.create();
        UUID teamId = UUID.randomUUID();
        runtime.scans()
            .restore(teamId, List.of(activeScan(teamId, CelestialObjectId.MARS)));

        new FacilityPersistenceManager(runtime).loadFromSaveDirectory(tempDir.toFile());

        assertEquals(
            List.of(),
            runtime.scans()
                .snapshots(teamId));
    }

    @Test
    void worldReloadDropsLogisticSignalsOfAssetsThatNoLongerExist(@TempDir Path tempDir) {
        CelestialServerRuntime runtime = CelestialServerRuntime.create();
        AutomatedFacility station = createStationWithFullLayout();
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        station.insert(resource, 3);
        station.logisticsConfig.set(resource, new LogisticsResourceConfig(15, 64, true, false));
        LogisticStore.updateSignalsForFacility(station);
        assertFalse(
            LogisticStore.allSignalsForScope(LogisticSignal.Scope.SYSTEM)
                .isEmpty(),
            "precondition: the station emits a signal");

        new FacilityPersistenceManager(runtime).loadFromSaveDirectory(tempDir.toFile());

        assertEquals(
            Map.of(),
            LogisticStore.allSignalsForScope(LogisticSignal.Scope.SYSTEM),
            "signals of assets from the previous world must not survive the reload");
    }

    @Test
    void discoveryFileReplacesScansForTeamsItOmits(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("_discovery.json");
        UUID retainedTeam = UUID.randomUUID();
        UUID omittedTeam = UUID.randomUUID();
        CelestialServerRuntime saved = CelestialServerRuntime.create();
        CelestialDiscoveryScanSnapshot retained = activeScan(retainedTeam, CelestialObjectId.MARS);
        saved.scans()
            .restore(retainedTeam, List.of(retained));
        new CelestialDiscoveryPersistenceAdapter(saved.scans()).save(file.toFile(), PERSISTENCE_GSON);

        CelestialServerRuntime loaded = CelestialServerRuntime.create();
        loaded.scans()
            .restore(omittedTeam, List.of(activeScan(omittedTeam, CelestialObjectId.EGORA)));

        new CelestialDiscoveryPersistenceAdapter(loaded.scans()).load(file.toFile(), PERSISTENCE_GSON);

        assertEquals(
            List.of(retained),
            loaded.scans()
                .snapshots(retainedTeam));
        assertEquals(
            List.of(),
            loaded.scans()
                .snapshots(omittedTeam));
    }

    @Test
    void malformedDiscoveryFileDoesNotPartiallyReplaceRuntimeScans(@TempDir Path tempDir) throws Exception {
        UUID residentTeam = UUID.randomUUID();
        CelestialDiscoveryScanSnapshot resident = activeScan(residentTeam, CelestialObjectId.MARS);
        CelestialServerRuntime runtime = CelestialServerRuntime.create();
        runtime.scans()
            .restore(residentTeam, List.of(resident));
        Path file = tempDir.resolve("_discovery.json");
        Files.writeString(
            file,
            "{\"teams\":[{\"teamId\":\"" + UUID.randomUUID() + "\",\"scans\":[]},{\"teamId\":null,\"scans\":[]}]}");

        assertThrows(
            IllegalStateException.class,
            () -> new CelestialDiscoveryPersistenceAdapter(runtime.scans()).load(file.toFile(), PERSISTENCE_GSON));
        assertEquals(
            List.of(resident),
            runtime.scans()
                .snapshots(residentTeam));
    }

    @Test
    void duplicateDiscoveryScanKeyDoesNotReplaceRuntimeScans(@TempDir Path tempDir) throws Exception {
        UUID residentTeam = UUID.randomUUID();
        CelestialDiscoveryScanSnapshot resident = activeScan(residentTeam, CelestialObjectId.EGORA);
        CelestialServerRuntime runtime = CelestialServerRuntime.create();
        runtime.scans()
            .restore(residentTeam, List.of(resident));
        UUID duplicateTeam = UUID.randomUUID();
        Path file = tempDir.resolve("_discovery.json");
        String duplicateIdentity = "\"anchor\":{\"kind\":\"registered\",\"bodyId\":\"MARS\",\"index\":0},"
            + "\"radius\":2.5,\"revision\":7,\"capability\":\"PROSPECTING\",";
        Files.writeString(
            file,
            "{\"teams\":[{\"teamId\":\"" + duplicateTeam
                + "\",\"scans\":[{"
                + duplicateIdentity
                + "\"status\":\"ACTIVE\",\"target\":{\"kind\":\"registered\",\"bodyId\":\"MOON\",\"index\":0},"
                + "\"step\":\"DETECTION\",\"elapsedTicks\":3},{"
                + duplicateIdentity
                + "\"status\":\"COMPLETE\",\"target\":null,\"step\":null,\"elapsedTicks\":0}]}]}");

        assertThrows(
            IllegalArgumentException.class,
            () -> new CelestialDiscoveryPersistenceAdapter(runtime.scans()).load(file.toFile(), PERSISTENCE_GSON));
        assertEquals(
            List.of(resident),
            runtime.scans()
                .snapshots(residentTeam));
    }

    @Test
    void fullAutomatedFacilityLoadsFromSaveFile(@TempDir Path tempDir) throws Exception {
        CelestialServerRuntime runtime = CelestialServerRuntime.create();
        FacilityPersistenceManager manager = new FacilityPersistenceManager(runtime);
        UUID teamId = UUID.randomUUID();
        AutomatedFacility station = createStationWithFullLayout();
        station.setStationFeatureSalt(0x5EED_1234_ABCDL);

        CelestialAssetStore.clear();
        CelestialAssetStore.registerAsset(teamId, station);
        NBTTagCompound encoded = facilityTag(station);
        manager.saveToSaveDirectory(tempDir.toFile());

        CelestialAssetStore.clear();
        assertDoesNotThrow(() -> manager.loadFromSaveDirectory(tempDir.toFile()));

        AutomatedFacility loaded = (AutomatedFacility) CelestialAssetStore.findAsset(station.assetId);
        assertNotNull(loaded);
        assertEquals(teamId, CelestialAssetStore.getTeamId(station.assetId));
        assertEquals(station.getEnergyStored(), loaded.getEnergyStored());
        assertEquals(station.stationFeatureSalt(), loaded.stationFeatureSalt());
        assertEquals(
            station.modules()
                .size(),
            loaded.modules()
                .size());
        assertLayoutEquals(station.stationLayout(), loaded.stationLayout());
        assertEquals(encoded, facilityTag(loaded));
    }

    @Test
    void asteroidScanProgressAndCompletionsRoundTripThroughSaveFile(@TempDir Path tempDir) {
        CelestialServerRuntime runtime = CelestialServerRuntime.create();
        FacilityPersistenceManager manager = new FacilityPersistenceManager(runtime);
        UUID teamId = UUID.fromString("00000000-0000-0000-0000-000000000271");
        MinorCelestialBodyId progressAsteroid = new MinorCelestialBodyId(
            CelestialObjectId.FROZEN_BELT,
            AsteroidSlotRanges.GENERATED_SLOT_MIN);
        MinorCelestialBodyId completedAsteroid = new MinorCelestialBodyId(
            CelestialObjectId.FROZEN_BELT,
            AsteroidSlotRanges.GENERATED_SLOT_MIN + 1);
        CelestialObjectKey progressKey = CelestialObjectKey.minorBody(progressAsteroid);
        CelestialDiscoveryScanSnapshot progress = new CelestialDiscoveryScanSnapshot(
            teamId,
            progressKey,
            0.5,
            1,
            CelestialDiscoveryCapability.PROSPECTING,
            CelestialDiscoveryScanSnapshot.Status.ACTIVE,
            progressKey,
            CelestialDiscoveryStep.PROFILE,
            600);
        CelestialDiscoveryScanSnapshot completion = CelestialDiscoveryScanSnapshot.complete(
            teamId,
            new CelestialDiscoveryScanScope(CelestialObjectKey.minorBody(completedAsteroid), 0.5, 1),
            CelestialDiscoveryCapability.PROSPECTING);
        runtime.scans()
            .restore(teamId, List.of(progress, completion));

        manager.saveToSaveDirectory(tempDir.toFile());
        SatelliteNetworkService.clear();

        manager.loadFromSaveDirectory(tempDir.toFile());

        assertEquals(
            List.of(progress, completion),
            runtime.scans()
                .snapshots(teamId));
    }

    @Test
    void saveFileRoundTripsStructuredMinorCelestialObjectKey(@TempDir Path tempDir) throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        UUID teamId = UUID.randomUUID();
        CelestialObjectKey key = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN));
        CelestialAsset asset = CelestialAsset
            .create(key, CelestialAsset.Kind.AUTOMATED_OUTPOST, Buildable.Status.OPERATIONAL);

        CelestialAssetStore.clear();
        CelestialAssetStore.registerAsset(teamId, asset);
        manager.saveToSaveDirectory(tempDir.toFile());

        CelestialAssetStore.clear();
        manager.loadFromSaveDirectory(tempDir.toFile());

        CelestialAsset loaded = CelestialAssetStore.findAsset(asset.assetId);
        assertNotNull(loaded);
        assertEquals(teamId, CelestialAssetStore.getTeamId(asset.assetId));
        assertEquals(key, loaded.celestialObjectKey);
    }

    @Test
    void missingStructuredPersistedCelestialObjectKeyFailsLoadLoudly(@TempDir Path tempDir) throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        NBTTagCompound missingStructuredKey = assetTag(
            manager,
            UUID.randomUUID(),
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            CelestialObjectId.MARS);
        missingStructuredKey.removeTag("body");
        writeAssetFile(tempDir, missingStructuredKey);

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> manager.loadFromSaveDirectory(tempDir.toFile()));
        assertFailureChainContains(thrown, ".body");
    }

    @Test
    void stationFeatureSaltRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = createStationWithFullLayout();
        station.setStationFeatureSalt(0x5EED_1234_ABCDL);

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());

        decoded = decodeFacility(decoded, encoded);

        assertEquals(station.stationFeatureSalt(), decoded.stationFeatureSalt());
    }

    @Test
    void hammerVariantRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = createStationWithFullLayout();
        ModuleHammer hammer = (ModuleHammer) station.modules()
            .get(0)
            .component();
        station.modules()
            .get(0)
            .setTier(ModuleTier.LuV);
        hammer.setVariant(HammerVariant.BIG);

        NBTTagCompound encoded = facilityTag(station);
        assertEquals(
            "BIG",
            moduleTag(encoded, 0).getCompoundTag("data")
                .getString("variant"));

        AutomatedFacility decoded = decodeFacility(emptyReplacement(station), encoded);

        ModuleHammer decodedHammer = (ModuleHammer) decoded.modules()
            .get(0)
            .component();
        assertEquals(HammerVariant.BIG, decodedHammer.variant());
    }

    @Test
    void minerBlacklistRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance miner = station.modules()
            .get(1);
        setMinerOreBlacklisted(station, miner, "ore:iron", true);

        AutomatedFacility decoded = emptyReplacement(station);
        decoded = decodeFacility(decoded, facilityTag(station));

        assertTrue(
            decoded.isMinerOreBlacklisted(
                decoded.modules()
                    .get(1),
                "ore:iron"));
    }

    @Test
    void minerFocusRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = createStationWithFullLayout();
        ModuleMiner miner = (ModuleMiner) station.modules()
            .get(1)
            .component();
        miner.setFocus(MinerFocusTier.III, "ore:iron", 1200);

        AutomatedFacility decoded = emptyReplacement(station);
        decoded = decodeFacility(decoded, facilityTag(station));

        ModuleMiner decodedMiner = (ModuleMiner) decoded.modules()
            .get(1)
            .component();
        assertEquals(MinerFocusTier.III, decodedMiner.focusTier());
        assertEquals("ore:iron", decodedMiner.focusOreKeyOrNull());
        assertEquals(1200, decodedMiner.focusAlignmentProgress());
    }

    @Test
    void hammerEnergyBufferRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = createStationWithFullLayout();
        ModuleHammer hammer = (ModuleHammer) station.modules()
            .get(0)
            .component();
        hammer.setEnergyStored(234_567L);

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = decodeFacility(emptyReplacement(station), encoded);

        ModuleHammer decodedHammer = (ModuleHammer) decoded.modules()
            .get(0)
            .component();
        assertEquals(234_567L, decodedHammer.energyStored());
    }

    @Test
    void hammerDispatchCooldownsRoundTripThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance module = station.modules()
            .get(0);
        module.setTier(ModuleTier.IV);
        ModuleHammer hammer = (ModuleHammer) module.component();
        hammer.markShotDispatched(module);
        hammer.markRouteProbeAttempted();

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());
        decoded = decodeFacility(decoded, encoded);

        ModuleHammer decodedHammer = (ModuleHammer) decoded.modules()
            .get(0)
            .component();
        assertEquals(hammer.shotCooldownTicks(), decodedHammer.shotCooldownTicks());
        assertEquals(hammer.routeProbeCooldownTicks(), decodedHammer.routeProbeCooldownTicks());
    }

    @Test
    void minerFocusTierWithoutOreRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = createStationWithFullLayout();
        ModuleMiner miner = (ModuleMiner) station.modules()
            .get(1)
            .component();
        miner.setFocus(MinerFocusTier.II, null, 1200);

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());
        decoded = decodeFacility(decoded, encoded);

        ModuleMiner decodedMiner = (ModuleMiner) decoded.modules()
            .get(1)
            .component();
        assertEquals(MinerFocusTier.II, decodedMiner.focusTier());
        assertNull(decodedMiner.focusOreKeyOrNull());
        assertEquals(0, decodedMiner.focusAlignmentProgress());
    }

    @Test
    void minerSettingsGroupRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance miner = station.modules()
            .get(1);
        setMinerOreBlacklisted(station, miner, "ore:iron", true);
        FacilityCommand.Result created = station.applyCommand(
            new FacilityCommand.CreateSettingsGroup(station.assetId, miner.id, "Shared miners"),
            FacilityCommand.Authority.NONE);
        assertEquals(FacilityCommand.Status.CHANGED, created.status());
        SettingsGroup.ID groupId = ((ModuleInstance.SettingsBinding.Shared) miner.settingsBinding()).groupId();
        assertNotNull(groupId);

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());
        decoded = decodeFacility(decoded, encoded);

        ModuleInstance decodedMiner = decoded.modules()
            .get(1);
        assertEquals(new ModuleInstance.SettingsBinding.Shared(groupId), decodedMiner.settingsBinding());
        assertTrue(decoded.isMinerOreBlacklisted(decodedMiner, "ore:iron"));
        assertEquals(
            "Shared miners",
            decoded.settingsGroup(groupId)
                .displayName());
    }

    @Test
    void privateMinerSettingsAreKeyedByStableModuleId() {
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance miner = station.modules()
            .get(1);
        setMinerOreBlacklisted(station, miner, "ore:copper", true);

        NBTTagCompound encoded = facilityTag(station);

        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());
        decoded = decodeFacility(decoded, encoded);
        ModuleInstance decodedMiner = decoded.modules()
            .get(1);

        assertTrue(decoded.isMinerOreBlacklisted(decodedMiner, "ore:copper"));
        assertTrue(decodedMiner.settingsBinding() instanceof ModuleInstance.SettingsBinding.Private);
    }

    @Test
    void moduleOperationRoundTripsThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance hammer = station.modules()
            .get(0);
        ModuleOperationState operation = ModuleOperationState
            .waiting(hammerOperationPlan(hammer, ModuleTier.LuV, HammerVariant.BIG, true, true))
            .withDepositedResources(Map.of("minecraft:iron_ingot:0", 8L))
            .beginBuilding()
            .tickBuilding();
        hammer.setOperation(operation);

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());
        decoded = decodeFacility(decoded, encoded);

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
    void pendingDeconstructionRefundRoundTripsThroughPersistence() {
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance module = station.modules()
            .get(2);
        module.updateStatus(Buildable.Status.DECONSTRUCTION);
        module.setOperation(ModuleOperationState.deconstructing(Map.of("minecraft:gold_ingot:0", 7L)));

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());
        decoded = decodeFacility(decoded, encoded);

        ModuleInstance decodedModule = decoded.modules()
            .get(2);
        assertEquals(Buildable.Status.DECONSTRUCTION, decodedModule.status());
        assertEquals(
            ModuleOperationPhase.REFUNDING,
            decodedModule.operationOrNull()
                .phase());
        assertTrue(
            decodedModule.operationOrNull()
                .plan()
                .spec() instanceof ModuleDeconstructionOperation);
        assertEquals(
            7L,
            decodedModule.operationOrNull()
                .refundBuffer()
                .get("minecraft:gold_ingot:0"));
    }

    @Test
    void moduleOperationRoundTripPreservesPlannedBuildTicks() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
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

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());
        decoded = decodeFacility(decoded, encoded);

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
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance module = station.modules()
            .get(1);
        module.setOperation(
            ModuleOperationState
                .waiting(new ModuleOperationPlan(new ModuleTierOperation(ModuleTier.IV), 37, Map.of(), false)));

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());
        decoded = decodeFacility(decoded, encoded);

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
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = createStationWithFullLayout();
        ModuleInstance hammer = station.modules()
            .get(0);
        hammer.setOperation(
            ModuleOperationState.waiting(hammerOperationPlan(hammer, ModuleTier.IV, HammerVariant.BASE, false, false)));
        NBTTagCompound encoded = facilityTag(station);
        moduleTag(encoded, 0).getCompoundTag("operation")
            .setString("phase", "BROKEN");

        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());

        assertThrows(IllegalStateException.class, () -> decodeFacility(decoded, encoded));
    }

    @Test
    void malformedAssetFileCrashesInsteadOfSkippingAsset(@TempDir Path tempDir) throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        UUID teamId = UUID.randomUUID();

        NBTTagCompound station = assetTag(manager, teamId, CelestialAsset.Kind.STATION, CelestialObjectId.MOON);
        NBTTagCompound outpost = assetTag(
            manager,
            teamId,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            CelestialObjectId.MARS);
        outpost.setTag("facility", malformedFacilityState());
        writeAssetFile(tempDir, List.of(station, outpost));

        CelestialAssetStore.clear();
        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> manager.loadFromSaveDirectory(tempDir.toFile()));
        assertFailureChainContains(thrown, ".inventory");
        assertNull(CelestialAssetStore.findAsset(CelestialAsset.ID.from(station.getString("id"))));
        assertNull(CelestialAssetStore.findAsset(CelestialAsset.ID.from(outpost.getString("id"))));
    }

    @Test
    void assetBoundsRoundTripThroughSaveFile(@TempDir Path tempDir) throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        UUID teamId = UUID.randomUUID();
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        station.setBound(resource, 48L, true);
        station.setBound(resource, 96L, false);

        CelestialAssetStore.clear();
        CelestialAssetStore.registerAsset(teamId, station);
        manager.saveToSaveDirectory(tempDir.toFile());

        CelestialAssetStore.clear();
        assertDoesNotThrow(() -> manager.loadFromSaveDirectory(tempDir.toFile()));

        AutomatedFacility loaded = (AutomatedFacility) CelestialAssetStore.findAsset(station.assetId);
        assertNotNull(loaded);
        assertEquals(
            48L,
            loaded.getBound(resource)
                .lowOrDefault());
        assertEquals(
            96L,
            loaded.getBound(resource)
                .upperOrDefault());
    }

    @Test
    void assetFiltersLoadFromSaveFile(@TempDir Path tempDir) throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        UUID teamId = UUID.randomUUID();
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        facility.setFilters(List.of("ore:iron", "ore:copper"), true);
        facility.setFilters(List.of(FluidRegistry.WATER.getName()), false);
        CelestialAssetStore.clear();
        CelestialAssetStore.registerAsset(teamId, facility);
        manager.saveToSaveDirectory(tempDir.toFile());

        CelestialAssetStore.clear();
        assertDoesNotThrow(() -> manager.loadFromSaveDirectory(tempDir.toFile()));

        AutomatedFacility loaded = (AutomatedFacility) CelestialAssetStore.findAsset(facility.assetId);
        assertNotNull(loaded);
        assertEquals(
            List.of("ore:iron", "ore:copper"),
            loaded.filtersSnapshot()
                .get(true));
        assertEquals(
            List.of(FluidRegistry.WATER.getName()),
            loaded.filtersSnapshot()
                .get(false));
    }

    @Test
    void invalidPersistedInventoryStateRejectsFacilityBeforeRegistration(@TempDir Path tempDir) throws Exception {
        List<Consumer<NBTTagCompound>> invalidStates = List.of(state -> {
            NBTTagCompound entry = itemEntry(new ItemStack(Items.iron_ingot), 0L);
            NBTTagList inventory = new NBTTagList();
            inventory.appendTag(entry);
            state.setTag("inventory", inventory);
        }, state -> state.removeTag("inventory"), state -> state.removeTag("itemFilters"));

        for (Consumer<NBTTagCompound> invalidState : invalidStates) {
            FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
            NBTTagCompound asset = facilityAssetTag(manager);
            NBTTagCompound facility = asset.getCompoundTag("facility");
            invalidState.accept(facility);

            writeAssetFile(tempDir, asset);
            CelestialAssetStore.clear();

            IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> manager.loadFromSaveDirectory(tempDir.toFile()));
            assertTrue(
                thrown.getMessage()
                    .contains("[PERSIST]"));
            assertNull(CelestialAssetStore.findAsset(CelestialAsset.ID.from(asset.getString("id"))));
        }
    }

    @Test
    void inventoryFiltersAndOverCapacityItemsRoundTripThroughSaveFile(@TempDir Path tempDir) throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        UUID teamId = UUID.randomUUID();
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ItemStackWrapper item = new ItemStackWrapper(Items.iron_ingot, 0, null);
        FluidKey fluid = new FluidKey(FluidRegistry.WATER, null);
        long storedItems = station.itemCapacity() + 1L;
        String itemFilter = item.toItemStack()
            .getUnlocalizedName();
        String fluidFilter = fluid.fluid()
            .getName();
        station.restoreInventory(Map.of(item, storedItems, fluid, 4096L));
        station.setFilters(List.of(itemFilter), true);
        station.setFilters(List.of(fluidFilter), false);

        CelestialAssetStore.clear();
        CelestialAssetStore.registerAsset(teamId, station);
        manager.saveToSaveDirectory(tempDir.toFile());
        CelestialAssetStore.clear();
        manager.loadFromSaveDirectory(tempDir.toFile());

        AutomatedFacility loaded = (AutomatedFacility) CelestialAssetStore.findAsset(station.assetId);
        assertNotNull(loaded);
        assertEquals(storedItems, loaded.itemAmount(item));
        assertEquals(4096L, loaded.fluidAmount(fluid));
        assertEquals(
            List.of(itemFilter),
            loaded.filtersSnapshot()
                .get(true));
        assertEquals(
            List.of(fluidFilter),
            loaded.filtersSnapshot()
                .get(false));
    }

    private static NBTTagCompound assetTag(FacilityPersistenceManager manager, UUID teamId, CelestialAsset.Kind kind,
        CelestialObjectId body) {
        CelestialAsset asset = CelestialAsset
            .create(CelestialObjectKey.registered(body), kind, Buildable.Status.OPERATIONAL);
        CelestialAssetStore.clear();
        CelestialAssetStore.registerAsset(teamId, asset);
        NBTTagCompound tag = AssetState.encode(teamId, asset);
        CelestialAssetStore.clear();
        return tag;
    }

    private static NBTTagCompound facilityAssetTag(FacilityPersistenceManager manager) {
        return assetTag(manager, UUID.randomUUID(), CelestialAsset.Kind.AUTOMATED_STATION, CelestialObjectId.MARS);
    }

    private static void writeAssetFile(Path tempDir, NBTTagCompound asset) throws Exception {
        writeAssetFile(tempDir, List.of(asset));
    }

    private static void writeAssetFile(Path tempDir, List<NBTTagCompound> assets) throws Exception {
        Path dataDir = tempDir.resolve("galaxiadata");
        Files.createDirectories(dataDir);
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("version", 1);
        NBTTagList list = new NBTTagList();
        assets.forEach(list::appendTag);
        root.setTag("assets", list);
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(
            dataDir.resolve("_assets.dat")
                .toFile())) {
            net.minecraft.nbt.CompressedStreamTools.writeCompressed(root, output);
        }
    }

    private static CelestialDiscoveryScanSnapshot activeScan(UUID teamId, CelestialObjectId anchor) {
        return new CelestialDiscoveryScanSnapshot(
            teamId,
            CelestialObjectKey.registered(anchor),
            2.5,
            7L,
            CelestialDiscoveryCapability.PROSPECTING,
            CelestialDiscoveryScanSnapshot.Status.ACTIVE,
            CelestialObjectKey.registered(CelestialObjectId.MOON),
            CelestialDiscoveryStep.DETECTION,
            3L);
    }

    private static NBTTagCompound malformedFacilityState() {
        AutomatedFacility source = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        NBTTagCompound facility = facilityTag(source);
        facility.removeTag("inventory");
        return facility;
    }

    private static NBTTagCompound itemEntry(ItemStack item, long amount) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("type", "item");
        NBTTagCompound stack = new NBTTagCompound();
        item.writeToNBT(stack);
        entry.setTag("stack", stack);
        entry.setLong("amount", amount);
        return entry;
    }

    private static NBTTagCompound facilityTag(AutomatedFacility facility) {
        return AssetState.encode(new UUID(0L, 1L), facility)
            .getCompoundTag("facility");
    }

    private static AutomatedFacility decodeFacility(AutomatedFacility template, NBTTagCompound facility) {
        NBTTagCompound asset = AssetState.encode(new UUID(0L, 1L), template);
        asset.setTag("facility", facility);
        return (AutomatedFacility) AssetState.decode(asset)
            .asset();
    }

    private static AutomatedFacility emptyReplacement(AutomatedFacility source) {
        return new AutomatedFacility(source.assetId, source.celestialObjectKey, source.kind, source.status());
    }

    private static NBTTagCompound moduleTag(NBTTagCompound facility, int index) {
        return facility.getTagList("modules", 10)
            .getCompoundTagAt(index);
    }

    private static AutomatedFacility createStationWithFullLayout() {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
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
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        // QUAD_2x2 module
        ModuleInstance quad = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), FacilityModuleKind.HAMMER, null, ModuleShape.QUAD_2x2, ModuleTier.IV);
        quad.updateStatus(Buildable.Status.OPERATIONAL);
        quad.initAnchor(StationTileCoord.of(5, 5));
        quad.setRotation(1);
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

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());
        decoded = decodeFacility(decoded, encoded);

        StationLayout decodedLayout = decoded.stationLayout();
        assertNotNull(decodedLayout);

        // Assert QUAD_2x2 tiles exist
        StationTileCoord qa = StationTileCoord.of(5, 5);
        assertTrue(decodedLayout.isOccupied(qa));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(4, 5)));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(5, 6)));
        assertTrue(decodedLayout.isOccupied(StationTileCoord.of(4, 6)));

        // Assert tile states â€” all tiles derive OCCUPIED_OPERATIONAL from module status
        assertEquals(
            StationTileState.OCCUPIED_OPERATIONAL,
            decodedLayout.get(qa)
                .state());
        assertEquals(
            StationTileState.OCCUPIED_OPERATIONAL,
            decodedLayout.get(StationTileCoord.of(4, 5))
                .state());
        assertEquals(
            StationTileState.OCCUPIED_OPERATIONAL,
            decodedLayout.get(StationTileCoord.of(5, 6))
                .state());
        assertEquals(
            StationTileState.OCCUPIED_OPERATIONAL,
            decodedLayout.get(StationTileCoord.of(4, 6))
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
        assertEquals(1, quadAnchor.rotation());
        assertSame(quadAnchor, decodedLayout.moduleAt(StationTileCoord.of(4, 5)));
        assertSame(quadAnchor, decodedLayout.moduleAt(StationTileCoord.of(5, 6)));
        assertSame(quadAnchor, decodedLayout.moduleAt(StationTileCoord.of(4, 6)));

        ModuleInstance blockAnchor = decodedLayout.moduleAt(ba);
        assertNotNull(blockAnchor);
        assertSame(blockAnchor, decodedLayout.moduleAt(StationTileCoord.of(-4, -5)));
        assertSame(blockAnchor, decodedLayout.moduleAt(StationTileCoord.of(-4, -4)));

        // Tier-shrink: modify encoded state to use HV tier (invalid for HAMMER)
        assertEquals("HAMMER", moduleTag(encoded, 0).getString("kind"));
        moduleTag(encoded, 0).setString("tier", ModuleTier.HV.name());

        AutomatedFacility malformedTier = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());
        assertThrows(IllegalStateException.class, () -> decodeFacility(malformedTier, encoded));
    }

    @Test
    void phaseThreeModulesRoundTrip() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
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

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = decodeFacility(emptyReplacement(station), encoded);

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

    }

    @Test
    void fluidBufferRoundTripsThroughFacilityPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        FluidKey bufferKey = new FluidKey(TEST_FLUID_1, null);
        station.insert(bufferKey, 4096);

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = decodeFacility(emptyReplacement(station), encoded);

        assertEquals(4096, decoded.fluidAmount(bufferKey));
    }

    @Test
    void everyModuleKindSurvivesRoundTrip() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        // Create ALL module kinds with various statuses
        // Layout coordinates starting at (1,0) and spreading right/down â€” no overlaps
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
        createAndPlaceModule(
            station,
            FacilityModuleKind.DEBUG_DATA_GENERATOR,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.HV,
            StationTileCoord.of(2, 4));

        StationLayout layout = station.stationLayout();
        assertNotNull(layout);

        NBTTagCompound encoded = facilityTag(station);

        AutomatedFacility decoded = decodeFacility(emptyReplacement(station), encoded);

        // --- ASSERTIONS ---
        // Use assertAll to collect ALL failures
        org.junit.jupiter.api.Assertions.assertAll(
            "fullRoundTripAllKinds",
            () -> assertEquals(
                15,
                decoded.modules()
                    .size(),
                "Expected 15 modules, got " + decoded.modules()
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
            () -> assertLayoutTilesExist(decoded, StationTileCoord.of(2, 4), "DEBUG_DATA_GENERATOR anchor"),
            () -> assertLayoutEquals(layout, decoded.stationLayout()));
    }

    @Test
    void allModuleKindsWithMultiTileSurviveRoundTrip() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
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

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = decodeFacility(emptyReplacement(station), encoded);

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
            () -> assertTrue(decodedLayout.isOccupied(StationTileCoord.of(-6, -6)), "BLOCK child missing"));
    }

    @Test
    void moduleAnchorAndShapeNotNullAfterDecode() throws Exception {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        // All module kinds with explicit shapes and anchors
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

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());
        decoded = decodeFacility(decoded, encoded);

        for (ModuleInstance m : decoded.modules()) {
            assertNotNull(
                m.anchor(),
                "Module " + m.kind()
                    + " (id="
                    + m.id
                    + ") has null anchor after decode!"
                    + " This means layout tiles were not reconstructed for this module."
                    + dumpModuleState(m));
            assertNotNull(m.shape(), "Module " + m.kind() + " (id=" + m.id + ") has null shape after decode");
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
            StationTileCoord[] tiles = m.tiles();
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

    }

    @Test
    void recipeBookRoundTripsFluidStacksRecipeStatsAndSchedule() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        ModuleInstance macerator = createAndPlaceModule(
            station,
            FacilityModuleKind.MACERATOR,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.HV,
            StationTileCoord.of(2, 2));
        FluidStack[] fluidInputs = { new FluidStack(TEST_FLUID_1, 144) };
        FluidStack[] fluidOutputs = { new FluidStack(TEST_FLUID_2, 72) };
        ItemStack[] itemInputs = { new ItemStack(Items.iron_ingot, 2, 0) };
        ItemStack[] itemOutputs = { new ItemStack(Items.diamond, 1, 0) };
        NBTTagCompound itemTag = new NBTTagCompound();
        itemTag.setString("materialGrade", "refined");
        itemInputs[0].setTagCompound(itemTag);
        NBTTagCompound fluidTag = new NBTTagCompound();
        fluidTag.setInteger("temperature", 725);
        fluidInputs[0].tag = fluidTag;
        int[] outputChances = { 5000 };
        int[] fluidOutputChances = { 7500 };
        RecipeSnapshot snapshot = RecipeSnapshot.resolved(
            (byte) 1,
            7,
            itemInputs,
            itemOutputs,
            fluidInputs,
            fluidOutputs,
            outputChances,
            fluidOutputChances,
            320,
            480);
        long contentHash = snapshot.contentHash();
        station.setBound(new FluidKey(TEST_FLUID_1, null), 11, true);
        station.setBound(new FluidKey(TEST_FLUID_2, null), 22, false);
        RecipeBook expectedBook = new RecipeBook(
            List.of(new SavedRecipe(snapshot, true, 12L, (byte) 3, (byte) 4, "Fluid recipe")),
            RecipeSchedulerMode.PRIORITY,
            NotDoablePolicy.SKIP);
        assertSame(
            FacilityCommand.Result.CHANGED,
            station.applyCommand(
                new FacilityCommand.ReplaceRecipeBook(
                    station.assetId,
                    new RecipeBookOwner.Private(macerator.id),
                    expectedBook),
                FacilityCommand.Authority.NONE));
        station.restoreRecipeScheduleState(macerator, new RecipeScheduleState((byte) 0, (byte) 1));

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = emptyReplacement(station);
        decoded = decodeFacility(decoded, encoded);

        ModuleInstance decodedMacerator = decoded.modules()
            .stream()
            .filter(m -> m.kind() == FacilityModuleKind.MACERATOR)
            .findFirst()
            .orElseThrow();
        RecipeBook decodedBook = decoded.recipeBook(decodedMacerator);
        assertEquals(expectedBook, decodedBook);
        assertEquals(new RecipeScheduleState((byte) 0, (byte) 1), decoded.recipeScheduleState(decodedMacerator));
        SavedRecipe decodedSlot = decodedBook.recipes()
            .get(0);
        RecipeSnapshot decodedSnapshot = decodedSlot.recipe();
        assertEquals(320, decodedSnapshot.duration());
        assertEquals(480, decodedSnapshot.eut());
        assertEquals(contentHash, decodedSnapshot.contentHash());
        assertEquals(
            5000,
            decodedSnapshot.itemOutputs()
                .get(0)
                .effectiveChance());
        assertEquals(
            7500,
            decodedSnapshot.fluidOutputs()
                .get(0)
                .effectiveChance());
        ItemStack decodedInput = decodedSnapshot.itemInputs()
            .get(0)
            .itemStack();
        ItemStack decodedOutput = decodedSnapshot.itemOutputs()
            .get(0)
            .itemStack();
        FluidStack decodedFluidInput = decodedSnapshot.fluidInputs()
            .get(0)
            .fluidStack();
        FluidStack decodedFluidOutput = decodedSnapshot.fluidOutputs()
            .get(0)
            .fluidStack();
        assertEquals(Items.iron_ingot, decodedInput.getItem());
        assertEquals(2, decodedInput.stackSize);
        assertEquals(itemTag, decodedInput.getTagCompound());
        assertEquals(Items.diamond, decodedOutput.getItem());
        assertEquals(1, decodedOutput.stackSize);
        assertEquals(
            new FluidKey(TEST_FLUID_1, null).fluid()
                .getName(),
            fluidName(decodedFluidInput));
        assertEquals(144, decodedFluidInput.amount);
        assertEquals(fluidTag, decodedFluidInput.tag);
        assertEquals(
            new FluidKey(TEST_FLUID_2, null).fluid()
                .getName(),
            fluidName(decodedFluidOutput));
        assertEquals(72, decodedFluidOutput.amount);
        assertEquals(12L, decodedSlot.requestAmount());
        assertEquals("Fluid recipe", decodedSlot.displayName());
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

    @Test
    void unknownRecipeFluidIsRejectedDuringPersistenceDecode() {
        AutomatedFacility station = createStationWithSingleRecipe();
        NBTTagCompound encoded = facilityTag(station);
        firstEncodedRecipe(encoded).getTagList("fluidInputs", 10)
            .getCompoundTagAt(0)
            .getCompoundTag("stack")
            .setString("FluidName", "galaxia:unregistered_recipe_fluid");
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());

        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> decodeFacility(decoded, encoded));
        assertFailureChainContains(failure, "unknown or malformed fluid");
    }

    @Test
    void malformedRecipeStackIsRejectedDuringPersistenceDecode() {
        AutomatedFacility station = createStationWithSingleRecipe();
        NBTTagCompound encoded = facilityTag(station);
        firstEncodedRecipe(encoded).getTagList("itemInputs", 10)
            .getCompoundTagAt(0)
            .setString("stack", "wrong type");
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());

        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> decodeFacility(decoded, encoded));
        assertFailureChainContains(failure, "wrong type");
    }

    @Test
    void upkeepCreditsRoundTripThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        FluidKey coolant = new FluidKey(TEST_FLUID_1, null);
        station.loadUpkeepCredits(new UpkeepSettlement.Credits(Map.of(), Map.of(coolant, UpkeepAmount.parse("0.25"))));

        NBTTagCompound encoded = facilityTag(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectKey,
            station.kind,
            station.status());
        decoded = decodeFacility(decoded, encoded);

        assertEquals(
            "0.25",
            decoded.upkeepCredits()
                .fluidCredits()
                .get(coolant)
                .toDisplayString());
    }

    // â”€â”€ Helpers â”€â”€

    @Test
    void upkeepReserveSettingsRoundTripThroughPersistence() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ItemStackWrapper resource = new ItemStackWrapper(Items.diamond, 0, null);
        station.setUpkeepReserve(resource, 17L);
        station.setUpkeepAutoOrder(resource, true);

        AutomatedFacility decoded = (AutomatedFacility) AssetState.decode(AssetState.encode(new UUID(0L, 1L), station))
            .asset();

        assertEquals(17L, decoded.upkeepReserve(resource));
        assertTrue(decoded.isUpkeepAutoOrderEnabled(resource));
    }

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

    private static AutomatedFacility createStationWithSingleRecipe() {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance macerator = createAndPlaceModule(
            station,
            FacilityModuleKind.MACERATOR,
            Buildable.Status.OPERATIONAL,
            ModuleShape.SINGLE,
            ModuleTier.HV,
            StationTileCoord.of(2, 2));
        RecipeSnapshot snapshot = RecipeSnapshot.resolved(
            (byte) 1,
            7,
            new ItemStack[] { new ItemStack(Items.iron_ingot) },
            new ItemStack[] { new ItemStack(Items.diamond) },
            new FluidStack[] { new FluidStack(TEST_FLUID_1, 144) },
            null,
            320,
            480);
        RecipeBook book = new RecipeBook(
            List.of(new SavedRecipe(snapshot, true, 12L, (byte) 3, (byte) 4, "Fluid recipe")),
            RecipeSchedulerMode.PRIORITY,
            NotDoablePolicy.SKIP);
        assertSame(
            FacilityCommand.Result.CHANGED,
            station.applyCommand(
                new FacilityCommand.ReplaceRecipeBook(station.assetId, new RecipeBookOwner.Private(macerator.id), book),
                FacilityCommand.Authority.NONE));
        return station;
    }

    private static NBTTagCompound firstEncodedRecipe(NBTTagCompound encoded) {
        NBTTagList modules = encoded.getTagList("modules", 10);
        for (int i = 0; i < modules.tagCount(); i++) {
            NBTTagCompound binding = modules.getCompoundTagAt(i)
                .getCompoundTag("settingsBinding");
            NBTTagCompound settings = binding.getCompoundTag("settings");
            if (!binding.getBoolean("shared") && settings.hasKey("book", 10)) {
                return settings.getCompoundTag("book")
                    .getTagList("recipes", 10)
                    .getCompoundTagAt(0);
            }
        }
        throw new AssertionError("No private recipe settings found in encoded facility state");
    }

    private static void assertFailureChainContains(Throwable failure, String messagePart) {
        Throwable matchingCause = failure;
        while (matchingCause != null && (matchingCause.getMessage() == null || !matchingCause.getMessage()
            .contains(messagePart))) {
            matchingCause = matchingCause.getCause();
        }
        assertNotNull(matchingCause);
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

    private static FluidStack fluidStack(String fluidName, int amount) throws Exception {
        return TestFluidStacks.stack(fluidName, amount);
    }

    private static String fluidName(FluidStack stack) throws Exception {
        return TestFluidStacks.name(stack);
    }

    @Test
    void unknownModuleKindCrashesOnLoad() {
        AutomatedFacility station = createStationWithFullLayout();
        NBTTagCompound encoded = facilityTag(station);
        moduleTag(encoded, 0).setString("kind", "UNKNOWN_MODULE_KIND");

        assertThrows(IllegalStateException.class, () -> decodeFacility(emptyReplacement(station), encoded));
    }

    @Test
    void fullPersistenceRoundTripValidatesEveryModuleAndTile() {
        AutomatedFacility before = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

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
            createAndPlaceModule(
                before,
                kind,
                Buildable.Status.OPERATIONAL,
                shape,
                kind.defaultTier(),
                StationTileCoord.of(colX, rowY));
            colX += step;
        }

        StationLayout layoutBefore = before.stationLayout();
        assertNotNull(layoutBefore);
        AutomatedFacility after = (AutomatedFacility) AssetState.decode(AssetState.encode(new UUID(0L, 1L), before))
            .asset();

        assertEquals(
            before.modules()
                .size(),
            after.modules()
                .size());
        assertLayoutEquals(layoutBefore, after.stationLayout());

        EnumSet<FacilityModuleKind> afterKinds = EnumSet.noneOf(FacilityModuleKind.class);
        for (ModuleInstance m : after.modules()) {
            afterKinds.add(m.kind());
            for (StationTileCoord tile : m.tiles()) {
                assertTrue(
                    after.stationLayout()
                        .isOccupied(tile),
                    "Layout missing tile " + tile + " for module " + m.kind() + " at anchor " + m.anchor());
                assertSame(
                    m,
                    after.stationLayout()
                        .moduleAt(tile));
            }
        }
        assertEquals(EnumSet.allOf(FacilityModuleKind.class), afterKinds);
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

    @Test
    void activeLogisticsTasksRoundTripRegisteredAndMinorBodyKeys(@TempDir Path tempDir) throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        UUID teamId = UUID.randomUUID();
        CelestialObjectKey fromKey = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialObjectKey toKey = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN));
        CelestialAsset from = CelestialAsset
            .create(fromKey, CelestialAsset.Kind.AUTOMATED_OUTPOST, Buildable.Status.OPERATIONAL);
        CelestialAsset to = CelestialAsset
            .create(toKey, CelestialAsset.Kind.AUTOMATED_OUTPOST, Buildable.Status.OPERATIONAL);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);

        CelestialAssetStore.clear();
        LogisticStore.clearDeliveries();
        CelestialAssetStore.registerAsset(teamId, from);
        CelestialAssetStore.registerAsset(teamId, to);
        LogisticsDelivery.ID deliveryId = LogisticsDelivery.ID.create();
        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                deliveryId,
                from.assetId,
                to.assetId,
                resource,
                7L,
                42,
                LogisticSignal.Scope.SYSTEM,
                fromKey,
                toKey,
                12.5,
                3.25));

        manager.saveToSaveDirectory(tempDir.toFile());

        JsonObject taskJson = PERSISTENCE_GSON.fromJson(
            Files.readString(
                tempDir.resolve("galaxiadata")
                    .resolve("_tasks.json")),
            com.google.gson.JsonArray.class)
            .get(0)
            .getAsJsonObject();
        assertEquals(
            "registered",
            taskJson.getAsJsonObject("fromBodyId")
                .get("kind")
                .getAsString());
        assertEquals(
            "minor",
            taskJson.getAsJsonObject("toBodyId")
                .get("kind")
                .getAsString());

        CelestialAssetStore.clear();
        LogisticStore.clearDeliveries();
        CelestialAssetStore.registerAsset(teamId, from);
        CelestialAssetStore.registerAsset(teamId, to);
        manager.loadFromSaveDirectory(tempDir.toFile());

        assertEquals(
            1,
            LogisticStore.activeDeliveries()
                .size());
        LogisticsDelivery loaded = LogisticStore.activeDeliveries()
            .get(0);
        assertEquals(deliveryId, loaded.deliveryId);
        assertEquals(from.assetId, loaded.data.fromAssetId());
        assertEquals(to.assetId, loaded.data.toAssetId());
        assertEquals(fromKey, loaded.data.fromBodyKey());
        assertEquals(toKey, loaded.data.toBodyKey());
        assertEquals(7L, loaded.data.amount());
        assertEquals(42, loaded.getRemainingTicks());
        assertEquals(12.5, loaded.data.departureOrbitalTime());
        assertEquals(3.25, loaded.data.tofOrbitalOsu());
        LogisticStore.clearDeliveries();
    }

    private static void setMinerOreBlacklisted(AutomatedFacility facility, ModuleInstance module, String oreKey,
        boolean blacklisted) {
        facility.applyCommand(
            new FacilityCommand.ReplaceMinerSettings(
                facility.assetId,
                module.id,
                facility.minerSettings(module)
                    .withOreBlacklisted(oreKey, blacklisted)),
            FacilityCommand.Authority.NONE);
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
