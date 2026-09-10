package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.state.AssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ModuleConstructionTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @AfterEach
    void clearRuntime() {
        CelestialAssetStore.SERVER.clearInternal();
        SatelliteNetworkService.clear();
    }

    @Test
    void completedConstructionBecomesVisibleToSatelliteJobs() {
        AutomatedFacility facility = facility();
        FacilityCommand.Authority debug = new FacilityCommand.Authority(false, true);
        for (int x = 1; x <= 2; x++) {
            assertEquals(
                FacilityCommand.Status.CHANGED,
                facility
                    .applyCommand(
                        new FacilityCommand.BuildModules(
                            facility.assetId,
                            FacilityModuleKind.DEBUG_DATA_GENERATOR,
                            FacilityModuleKind.DEBUG_DATA_GENERATOR.defaultShape(),
                            new IModuleComponent.BuildPhysicalSpec.Tier(ModuleTier.HV),
                            null,
                            x == 2,
                            List.of(ModulePlacement.at(StationTileCoord.of(x, 0)))),
                        debug)
                    .status());
        }
        ModuleInstance producer = facility.modules()
            .get(0);
        ModuleInstance consumer = facility.modules()
            .get(1);
        CelestialAssetStore.SERVER.registerAssetInternal(UUID.randomUUID(), facility);
        facility.applyCommand(
            new FacilityCommand.ConfigureDebugDataGenerator(
                facility.assetId,
                consumer.id,
                ModuleDebugDataGenerator.Config
                    .consume(SatelliteDataType.PROSPECTING, 10, 20, facility.celestialObjectKey)),
            debug);
        ModuleDebugDataGenerator producerData = (ModuleDebugDataGenerator) producer.component();
        SatelliteNetworkService.tickDataJobs();
        assertNull(producerData.detectedCounterpartBodyKey());
        FacilityModuleRegistry.operationCost(producer.getConstructionCost())
            .forEach((item, amount) -> facility.insert(item, amount));

        finishConstruction(facility, producer);
        assertTrue(producer.isOperational());
        SatelliteNetworkService.tickDataJobs();

        assertEquals(facility.celestialObjectKey, producerData.detectedCounterpartBodyKey());
    }

    @Test
    void survivalConstructionWaitsForMaterialsAndCompletesWithoutChargingTwice() {
        AutomatedFacility facility = facility();
        ModuleInstance module = buildMiner(facility);
        facility.tick();
        assertFalse(module.isOperational());
        Map<ItemStackWrapper, Long> cost = FacilityModuleRegistry.operationCost(module.getConstructionCost());
        cost.forEach((item, amount) -> facility.insert(item, amount * 2));

        finishConstruction(facility, module);

        assertTrue(module.isOperational());
        assertEquals(cost, facility.itemSnapshot());
    }

    @Test
    void cancellingPartiallyFundedConstructionReturnsDepositsAndReleasesPlacement() {
        for (boolean destroy : new boolean[] { false, true }) {
            AutomatedFacility facility = facility();
            ModuleInstance module = buildMiner(facility);
            ItemStackWrapper item = FacilityModuleRegistry.operationCost(module.getConstructionCost())
                .keySet()
                .iterator()
                .next();
            facility.insert(item, 1L);
            facility.tick();
            assertFalse(module.isOperational());
            assertTrue(
                facility.itemSnapshot()
                    .isEmpty());
            FacilityCommand cancellation = destroy
                ? new FacilityCommand.RequestModuleDeconstruction(facility.assetId, module.id)
                : new FacilityCommand.CancelModuleOperation(facility.assetId, module.id);

            assertEquals(
                FacilityCommand.Status.CHANGED,
                facility.applyCommand(cancellation, FacilityCommand.Authority.NONE)
                    .status());
            facility.tick();

            assertTrue(
                facility.modules()
                    .isEmpty());
            assertEquals(Map.of(item, 1L), facility.itemSnapshot());
            buildMiner(facility);
        }
    }

    @Test
    void constructionResumesAfterStateRoundTripWithoutLosingDeposits() {
        for (boolean fullyFunded : new boolean[] { false, true }) {
            AutomatedFacility facility = facility();
            ModuleInstance module = buildMiner(facility);
            Map<ItemStackWrapper, Long> cost = FacilityModuleRegistry.operationCost(module.getConstructionCost());
            ItemStackWrapper first = cost.keySet()
                .iterator()
                .next();
            Map<ItemStackWrapper, Long> supplied = fullyFunded ? cost : Map.of(first, 1L);
            supplied.forEach((item, amount) -> facility.insert(item, amount));
            facility.tick();
            if (fullyFunded) facility.tick();

            AutomatedFacility restored = roundTrip(facility);
            ModuleInstance restoredModule = restored.moduleById(module.id);
            assertNotNull(restoredModule);
            cost.forEach((item, amount) -> restored.insert(item, amount - supplied.getOrDefault(item, 0L)));
            finishConstruction(restored, restoredModule);

            assertTrue(restoredModule.isOperational());
            assertTrue(
                restored.itemSnapshot()
                    .isEmpty());
        }
    }

    @Test
    void cancelledConstructionRetainsRefundUntilSpaceExistsAcrossStateRoundTrip() {
        AutomatedFacility facility = facility();
        ModuleInstance module = buildMiner(facility);
        ItemStackWrapper deposited = FacilityModuleRegistry.operationCost(module.getConstructionCost())
            .keySet()
            .iterator()
            .next();
        facility.insert(deposited, 1L);
        facility.tick();
        ItemStackWrapper filler = new ItemStackWrapper(Items.stick, 0, null);
        long capacity = facility.itemCapacity();
        facility.insert(filler, capacity);
        assertEquals(
            FacilityCommand.Status.CHANGED,
            facility
                .applyCommand(
                    new FacilityCommand.CancelModuleOperation(facility.assetId, module.id),
                    FacilityCommand.Authority.NONE)
                .status());
        assertFalse(
            facility.modules()
                .isEmpty());

        AutomatedFacility restored = roundTrip(facility);
        restored.tick();
        assertFalse(
            restored.modules()
                .isEmpty());
        restored.extract(filler, 1L);
        restored.tick();
        restored.tick();

        assertTrue(
            restored.modules()
                .isEmpty());
        assertEquals(Map.of(filler, capacity - 1L, deposited, 1L), restored.itemSnapshot());
    }

    private static AutomatedFacility roundTrip(AutomatedFacility facility) {
        return (AutomatedFacility) AssetState.decode(AssetState.encode(UUID.randomUUID(), facility))
            .asset();
    }

    private static void finishConstruction(AutomatedFacility facility, ModuleInstance module) {
        int duration = module.allTierData()
            .get(module.tier())
            .buildTicks();
        for (int i = 0; i <= duration * 5 + 1 && !module.isOperational(); i++) facility.tick();
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance buildMiner(AutomatedFacility facility) {
        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.BuildModules(
                facility.assetId,
                FacilityModuleKind.MINER,
                FacilityModuleKind.MINER.defaultShape(),
                new IModuleComponent.BuildPhysicalSpec.Miner(ModuleTier.EV, MinerFocusTier.NONE),
                null,
                false,
                List.of(ModulePlacement.at(StationTileCoord.of(1, 0)))),
            FacilityCommand.Authority.NONE);
        assertEquals(FacilityCommand.Status.CHANGED, result.status());
        return facility.modules()
            .getFirst();
    }
}
