package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.init.Items;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class HammerDispatchStatusTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @AfterEach
    void cleanup() {
        LogisticStore.clearDeliveries();
    }

    @Test
    void readyWhenCandidatePassesHammerDispatchChecks() {
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BIG, 1_000_000L);
        HammerDispatchStatus.Candidate candidate = candidate(64, 64, 32, 1.5, 20.0, 120.0);

        HammerDispatchStatus.Status status = HammerDispatchStatus.evaluateCandidate(hammer, candidate);

        assertEquals(HammerDispatchStatus.Code.READY, status.code());
        assertEquals(200_000L, status.requiredEnergy());
    }

    @Test
    void codesExposeDispatchPriorityDirectly() {
        assertEquals(100, HammerDispatchStatus.Code.READY.priority());
        assertEquals(80, HammerDispatchStatus.Code.BLOCKED_BY_DV_LIMIT.priority());
        assertEquals(80, HammerDispatchStatus.Code.BLOCKED_BY_TOF_LIMIT.priority());
        assertEquals(20, HammerDispatchStatus.Code.WAITING_FOR_REQUEST.priority());
    }

    @Test
    void sendsOneConfiguredPackageWhenMoreItemsAreRequested() {
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BIG, 1_000_000L);
        HammerDispatchStatus.Candidate candidate = candidate(64, 64, 32, 1.5, 20.0, 120.0);

        HammerDispatchStatus.Status status = HammerDispatchStatus.evaluateCandidate(hammer, candidate);

        assertEquals(HammerDispatchStatus.Code.READY, status.code());
        assertEquals(32L, status.sendAmount());
    }

    @Test
    void plannerReturnsReadyDispatchPlanForServerExecution() {
        AutomatedFacility supplier = facility(CelestialObjectId.PANSPIRA);
        AutomatedFacility requester = facility(CelestialObjectId.PANSPIRA);
        ItemStackWrapper resource = new ItemStackWrapper(Items.diamond, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(32, 32, false, true));
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(64, 32, true, false));
        supplier.updateItems(resource, 96);
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BASE, 1_000_000L);
        ModuleInstance hammerModule = hammerModule(hammer);

        HammerDispatchPlanner.Result result = HammerDispatchPlanner
            .evaluate(supplier, hammerModule, List.of(requester), 0.0);

        assertEquals(HammerDispatchStatus.Code.READY, result.code());
        HammerDispatchPlanner.Plan plan = result.plan();
        assertNotNull(plan);
        assertSame(supplier, plan.supplier());
        assertSame(requester, plan.requester());
        assertEquals(resource, plan.resource());
        assertEquals(32L, plan.sendAmount());
        assertEquals(10_000L, plan.requiredEnergy());
        assertEquals(LogisticSignal.Scope.PLANETARY, plan.deliveryScope());
        assertEquals(1, plan.travelTimeTicks());
    }

    @Test
    void reportsEnergyNeededWhenRouteCostExceedsPrivateBuffer() {
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BIG, 500_000L);
        HammerDispatchStatus.Candidate candidate = candidate(64, 64, 32, 1.5, 80.0, 120.0);

        HammerDispatchStatus.Status status = HammerDispatchStatus.evaluateCandidate(hammer, candidate);

        assertEquals(HammerDispatchStatus.Code.NEED_ENERGY, status.code());
        assertEquals(800_000L, status.requiredEnergy());
        assertEquals(500_000L, status.storedEnergy());
    }

    @Test
    void reportsDvLimitWhenShootingConfigBlocksRoute() {
        ModuleHammer hammer = hammer(
            new AllowShootingConfig(AllowShootingConfig.Mode.WHEN_DV_UNDER, 2.0),
            HammerVariant.BIG,
            1_000_000L);
        HammerDispatchStatus.Candidate candidate = candidate(64, 64, 32, 3.0, 20.0, 120.0);

        HammerDispatchStatus.Status status = HammerDispatchStatus.evaluateCandidate(hammer, candidate);

        assertEquals(HammerDispatchStatus.Code.BLOCKED_BY_DV_LIMIT, status.code());
    }

    @Test
    void reportsOrderBelowPackageSizeBeforeSpendingEnergy() {
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BIG, 1_000_000L);
        HammerDispatchStatus.Candidate candidate = candidate(64, 16, 32, 1.5, 20.0, 120.0);

        HammerDispatchStatus.Status status = HammerDispatchStatus.evaluateCandidate(hammer, candidate);

        assertEquals(HammerDispatchStatus.Code.ORDER_BELOW_PACKAGE_SIZE, status.code());
        assertEquals(16L, status.sendAmount());
        assertEquals(32, status.orderSize());
    }

    @Test
    void orderBelowPackageSizeDoesNotConsumeRouteProbe() {
        AutomatedFacility supplier = facility(CelestialObjectId.FROZEN_BELT);
        AutomatedFacility requester = facility(CelestialObjectId.PANSPIRA);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(16, 64, true, false));
        supplier.updateItems(resource, 128);
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BIG, 1_000_000L);
        ModuleInstance hammerModule = hammerModule(hammer);

        HammerDispatchPlanner.Result result = HammerDispatchPlanner
            .evaluate(supplier, hammerModule, requester, resource, 0.0, null);

        assertEquals(HammerDispatchStatus.Code.ORDER_BELOW_PACKAGE_SIZE, result.code());
        assertTrue(hammer.canPlanRoute(hammerModule));
    }

    @Test
    void reportsArrivedDeliveryBlockedAtDestinationBeforePackageSize() {
        AutomatedFacility supplier = facility(CelestialObjectId.PANSPIRA);
        AutomatedFacility requester = facility(CelestialObjectId.PANSPIRA);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(122, 64, true, false));
        supplier.updateItems(resource, 128);
        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                supplier.assetId,
                requester.assetId,
                resource,
                64L,
                0,
                LogisticSignal.Scope.PLANETARY,
                supplier.celestialObjectId,
                requester.celestialObjectId,
                0,
                0));
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BASE, 1_000_000L);

        HammerDispatchPlanner.Result result = HammerDispatchPlanner
            .evaluate(supplier, hammerModule(hammer), List.of(requester), 0.0);

        assertEquals(HammerDispatchStatus.Code.DESTINATION_CAPACITY_BLOCKED, result.code());
        assertEquals(64L, result.sendAmount());
    }

    @Test
    void skipsRequesterWithoutRoomForPackageAndContinuesScanning() {
        AutomatedFacility supplier = facility(CelestialObjectId.PANSPIRA);
        AutomatedFacility fullRequester = facility(CelestialObjectId.PANSPIRA);
        AutomatedFacility validRequester = facility(CelestialObjectId.PANSPIRA);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        ItemStackWrapper filler = new ItemStackWrapper(Items.diamond, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        fullRequester.logisticsConfig.set(resource, new LogisticsResourceConfig(128, 64, true, false));
        validRequester.logisticsConfig.set(resource, new LogisticsResourceConfig(128, 64, true, false));
        supplier.updateItems(resource, 256);
        fullRequester.updateItems(filler, fullRequester.totalItemCapacity());
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BASE, 1_000_000L);

        HammerDispatchPlanner.Result result = HammerDispatchPlanner
            .evaluate(supplier, hammerModule(hammer), List.of(fullRequester, validRequester), 0.0);

        assertEquals(HammerDispatchStatus.Code.READY, result.code());
        HammerDispatchPlanner.Plan plan = result.plan();
        assertNotNull(plan);
        assertSame(validRequester, plan.requester());
        assertEquals(64L, plan.sendAmount());
    }

    private static ModuleHammer hammer(AllowShootingConfig config, HammerVariant variant, long energyStored) {
        return new ModuleHammer(
            FacilityModuleKind.HAMMER,
            config,
            OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV,
            variant,
            64,
            energyStored);
    }

    private static HammerDispatchStatus.Candidate candidate(long availableSurplus, long requestedAmount, int orderSize,
        double departureDv, double totalDv, double tofSeconds) {
        return new HammerDispatchStatus.Candidate(
            false,
            true,
            true,
            availableSurplus,
            requestedAmount,
            orderSize,
            departureDv,
            totalDv,
            tofSeconds);
    }

    private static AutomatedFacility facility(CelestialObjectId bodyId) {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            bodyId,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance hammerModule(ModuleHammer hammer) {
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.HAMMER,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.LuV);
        module.setComponent(hammer);
        return module;
    }
}
