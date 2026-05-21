package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import net.minecraft.init.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.TestFMLRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
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

final class HammerDispatchStatusTest {

    @BeforeAll
    static void initRegistries() {
        TestFMLRegistry.init();
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
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
