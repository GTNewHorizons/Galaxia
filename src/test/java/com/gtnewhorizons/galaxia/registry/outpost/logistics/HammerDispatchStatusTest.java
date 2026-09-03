package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.TileHammerCannon;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
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

    private static final UUID TEST_TEAM = UUID.randomUUID();

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @AfterEach
    void cleanup() {
        LogisticStore.clearDeliveries();
        CelestialAssetStore.clear();
    }

    @Test
    void codesExposeDispatchPriorityDirectly() {
        assertEquals(100, HammerDispatchStatus.Code.READY.priority());
        assertEquals(80, HammerDispatchStatus.Code.BLOCKED_BY_DV_LIMIT.priority());
        assertEquals(80, HammerDispatchStatus.Code.BLOCKED_BY_TOF_LIMIT.priority());
        assertEquals(20, HammerDispatchStatus.Code.WAITING_FOR_REQUEST.priority());
    }

    @Test
    void plannerReturnsReadyDispatchPlanForServerExecution() {
        AutomatedFacility supplier = facility(CelestialObjectId.OVERWORLD);
        AutomatedFacility requester = facility(CelestialObjectId.OVERWORLD);
        ItemStackWrapper resource = new ItemStackWrapper(Items.diamond, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(32, 32, false, true));
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(64, 32, true, false));
        supplier.insert(resource, 96);
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BASE, 1_000_000L);
        ModuleInstance hammerModule = hammerModule(hammer);

        HammerDispatchPlanner.Result result = HammerDispatchPlanner
            .planDispatch(supplier, hammerModule, requester, resource, 0.0, null);

        assertEquals(HammerDispatchStatus.Code.READY, result.code());
        HammerDispatchPlanner.Plan plan = result.plan();
        assertNotNull(plan);
        assertSame(supplier, plan.supplier());
        assertSame(requester, plan.requester());
        assertEquals(resource, plan.resource());
        assertEquals(64L, plan.sendAmount());
        assertEquals(10_000L, plan.requiredEnergy());
        assertEquals(LogisticSignal.Scope.PLANETARY, plan.deliveryScope());
        assertEquals(1, plan.travelTimeTicks());
    }

    @Test
    void plannerRequestsPackageUpToRequesterEffectiveLowerBound() {
        AutomatedFacility supplier = facility(CelestialObjectId.OVERWORLD);
        AutomatedFacility requester = facility(CelestialObjectId.OVERWORLD);
        ItemStackWrapper resource = new ItemStackWrapper(Items.redstone, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        requester.applyCommand(
            new FacilityCommand.SetInventoryBound(requester.assetId, BoundKind.ITEM_LOWER, resource, 54),
            FacilityCommand.Authority.NONE);
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(10, 64, true, false));
        supplier.insert(resource, 128);
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BASE, 1_000_000L);

        HammerDispatchPlanner.Result result = HammerDispatchPlanner
            .planDispatch(supplier, hammerModule(hammer), requester, resource, 0.0, null);

        assertEquals(HammerDispatchStatus.Code.READY, result.code());
        assertEquals(64L, result.sendAmount());
    }

    @Test
    void plannerReportsEnergyNeededWhenPrivateBufferCannotPayForShot() {
        AutomatedFacility supplier = facility(CelestialObjectId.OVERWORLD);
        AutomatedFacility requester = facility(CelestialObjectId.OVERWORLD);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(64, 64, true, false));
        supplier.insert(resource, 64);
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BASE, 0L);

        HammerDispatchPlanner.Result result = HammerDispatchPlanner
            .planDispatch(supplier, hammerModule(hammer), requester, resource, 0.0, null);

        assertEquals(HammerDispatchStatus.Code.NEED_ENERGY, result.code());
        assertEquals(10_000L, result.requiredEnergy());
        assertEquals(0L, result.storedEnergy());
    }

    @Test
    void plannerReportsDvLimitWhenShootingConfigBlocksRoute() {
        AutomatedFacility supplier = facility(CelestialObjectId.FROZEN_BELT);
        AutomatedFacility requester = facility(CelestialObjectId.OVERWORLD);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(64, 64, true, false));
        supplier.insert(resource, 64);
        ModuleHammer hammer = hammer(
            new AllowShootingConfig(AllowShootingConfig.Mode.WHEN_DV_UNDER, 0.0),
            HammerVariant.BIG,
            1_000_000L);

        HammerDispatchPlanner.Result result = HammerDispatchPlanner
            .planDispatch(supplier, hammerModule(hammer), requester, resource, 0.0, null);

        assertEquals(HammerDispatchStatus.Code.BLOCKED_BY_DV_LIMIT, result.code());
    }

    @Test
    void statusInspectionDoesNotConsumeRouteProbeCooldown() {
        AutomatedFacility supplier = facility(CelestialObjectId.FROZEN_BELT);
        AutomatedFacility requester = facility(CelestialObjectId.OVERWORLD);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(64, 64, true, false));
        supplier.insert(resource, 64);
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BIG, 1_000_000L);

        HammerDispatchStatus.Status status = HammerDispatchStatus
            .evaluate(supplier, hammerModule(hammer), List.of(requester), 0.0);

        assertEquals(HammerDispatchStatus.Code.READY, status.code());
        assertEquals(0, hammer.routeProbeCooldownTicks());
    }

    @Test
    void plannerSendsAvailableSurplusWhenItIsBelowOrderSize() {
        AutomatedFacility supplier = facility(CelestialObjectId.OVERWORLD);
        AutomatedFacility requester = facility(CelestialObjectId.OVERWORLD);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(64, 32, true, false));
        supplier.insert(resource, 16);
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BASE, 1_000_000L);

        HammerDispatchPlanner.Result result = HammerDispatchPlanner
            .planDispatch(supplier, hammerModule(hammer), requester, resource, 0.0, null);

        assertEquals(HammerDispatchStatus.Code.READY, result.code());
        assertEquals(16L, result.sendAmount());
        assertEquals(32, result.orderSize());
    }

    @Test
    void plannerSendsRequestedAmountWhenRequestIsBelowOrderSize() {
        AutomatedFacility supplier = facility(CelestialObjectId.FROZEN_BELT);
        AutomatedFacility requester = facility(CelestialObjectId.OVERWORLD);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(16, 64, true, false));
        supplier.insert(resource, 128);
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BIG, 1_000_000L);
        ModuleInstance hammerModule = hammerModule(hammer);

        HammerDispatchPlanner.Result result = HammerDispatchPlanner
            .planDispatch(supplier, hammerModule, requester, resource, 0.0, null);

        assertEquals(HammerDispatchStatus.Code.READY, result.code());
        assertEquals(16L, result.sendAmount());
    }

    @Test
    void reportsArrivedDeliveryBlockedAtDestinationBeforePackageSize() {
        AutomatedFacility supplier = facility(CelestialObjectId.OVERWORLD);
        AutomatedFacility requester = facility(CelestialObjectId.OVERWORLD);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(122, 64, true, false));
        supplier.insert(resource, 128);
        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                supplier.assetId,
                requester.assetId,
                resource,
                64L,
                0,
                LogisticSignal.Scope.PLANETARY,
                supplier.celestialObjectKey,
                requester.celestialObjectKey,
                0,
                0,
                null));
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BASE, 1_000_000L);

        HammerDispatchStatus.Status result = HammerDispatchPlanner
            .inspect(supplier, hammerModule(hammer), List.of(requester), 0.0);

        assertEquals(HammerDispatchStatus.Code.DESTINATION_CAPACITY_BLOCKED, result.code());
        assertEquals(64L, result.sendAmount());
    }

    @Test
    void unavailablePhysicalDestinationQueuesOnlyTheOutstandingRequest() {
        AutomatedFacility supplier = facility(CelestialObjectId.OVERWORLD);
        Station requester = new Station(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.registerAsset(TEST_TEAM, requester);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(64, 64, true, false));
        supplier.insert(resource, 64);
        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                supplier.assetId,
                requester.assetId,
                resource,
                32L,
                0,
                LogisticSignal.Scope.PLANETARY,
                supplier.celestialObjectKey,
                requester.celestialObjectKey,
                0,
                0,
                null));

        HammerDispatchPlanner.Result result = HammerDispatchPlanner.planDispatch(
            supplier,
            hammerModule(hammer(AllowShootingConfig.ALWAYS, HammerVariant.BASE, 1_000_000L)),
            requester,
            resource,
            0.0,
            null);

        assertEquals(HammerDispatchStatus.Code.READY, result.code());
        assertEquals(32L, result.sendAmount());
    }

    @Test
    void skipsRequesterWithoutRoomForPackageAndContinuesScanning() {
        AutomatedFacility supplier = facility(CelestialObjectId.OVERWORLD);
        AutomatedFacility fullRequester = facility(CelestialObjectId.OVERWORLD);
        AutomatedFacility validRequester = facility(CelestialObjectId.OVERWORLD);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        ItemStackWrapper filler = new ItemStackWrapper(Items.diamond, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        fullRequester.logisticsConfig.set(resource, new LogisticsResourceConfig(128, 64, true, false));
        validRequester.logisticsConfig.set(resource, new LogisticsResourceConfig(128, 64, true, false));
        supplier.insert(resource, 256);
        fullRequester.insert(filler, fullRequester.itemCapacity());
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BASE, 1_000_000L);

        HammerDispatchStatus.Status result = HammerDispatchPlanner
            .inspect(supplier, hammerModule(hammer), List.of(fullRequester, validRequester), 0.0);

        assertEquals(HammerDispatchStatus.Code.READY, result.code());
        assertEquals(64L, result.sendAmount());
    }

    @Test
    void plannerCanEvaluateTeamFilteredClientAssetsWithoutTheServerStore() {
        AutomatedFacility supplier = unregisteredFacility(CelestialObjectId.OVERWORLD);
        AutomatedFacility requester = unregisteredFacility(CelestialObjectId.OVERWORLD);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);

        assertEquals(HammerDispatchStatus.Code.READY, plan(supplier, requester, resource).code());
    }

    @Test
    void plannerUsesItemSpecificCapacityForPhysicalDestination() {
        AutomatedFacility supplier = facility(CelestialObjectId.OVERWORLD);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        TestPhysicalInventoryAsset requester = new TestPhysicalInventoryAsset(resource, 8L);
        CelestialAssetStore.registerAsset(TEST_TEAM, requester);
        HammerDispatchPlanner.Result result = plan(supplier, requester, resource);

        assertEquals(HammerDispatchStatus.Code.DESTINATION_LACKS_PACKAGE_SPACE, result.code());
        assertEquals(8L, result.sendAmount());
    }

    @Test
    void plannerUsesTheCannonBelongingToTheSelectedHammerModule() {
        TileHammerCannon selectedCannon = cannonWith(stack(32, "selected"));
        TileHammerCannon otherCannon = cannonWith(stack(64, "other"));
        ItemStackWrapper resource = ItemStackWrapper.of(stack(1, "selected"));
        selectedCannon.getHammer()
            .setEnergyStored(1_000_000L);

        Station supplier = new Station(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            Buildable.Status.OPERATIONAL) {

            @Override
            public TileHammerCannon findHammerCannon(ModuleInstance module) {
                if (module == selectedCannon.getModuleInstance()) return selectedCannon;
                if (module == otherCannon.getModuleInstance()) return otherCannon;
                return null;
            }
        };
        AutomatedFacility requester = facility(CelestialObjectId.OVERWORLD);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(64, 64, true, false));

        HammerDispatchPlanner.Result result = HammerDispatchPlanner
            .planDispatch(supplier, selectedCannon.getModuleInstance(), requester, resource, 0.0, null);

        assertEquals(HammerDispatchStatus.Code.READY, result.code());
        assertEquals(32L, result.sendAmount());
        HammerDispatchPlanner.Plan plan = result.plan();
        assertNotNull(plan);
        assertEquals(resource, plan.resource());
        assertEquals(32L, selectedCannon.getPackageAmount(resource));
        assertEquals(64L, otherCannon.getPackageAmount(ItemStackWrapper.of(stack(1, "other"))));

        TileHammerCannon dispatchCannon = supplier.findHammerCannon(selectedCannon.getModuleInstance());
        assertSame(selectedCannon, dispatchCannon);
        assertTrue(dispatchCannon.tryExtractPackage(plan.resource(), plan.sendAmount()));

        assertEquals(0L, selectedCannon.getPackageAmount(resource));
        assertEquals(64L, otherCannon.getPackageAmount(ItemStackWrapper.of(stack(1, "other"))));
    }

    @Test
    void plannerRejectsDestinationWithoutCargoStorage() {
        AutomatedFacility supplier = facility(CelestialObjectId.OVERWORLD);
        CelestialAsset requester = new TestLogisticsAsset(CelestialAsset.Kind.SATELLITE);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        HammerDispatchPlanner.Result result = plan(supplier, requester, resource);

        assertEquals(HammerDispatchStatus.Code.DESTINATION_LACKS_PACKAGE_SPACE, result.code());
        assertEquals(0L, result.sendAmount());
    }

    private static HammerDispatchPlanner.Result plan(AutomatedFacility supplier, CelestialAsset requester,
        ItemStackWrapper resource) {
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(64, 64, true, false));
        supplier.insert(resource, 64);
        return HammerDispatchPlanner.planDispatch(
            supplier,
            hammerModule(hammer(AllowShootingConfig.ALWAYS, HammerVariant.BASE, 1_000_000L)),
            requester,
            resource,
            0.0,
            null);
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

    private static AutomatedFacility facility(CelestialObjectId bodyId) {
        return facility(bodyId, TEST_TEAM);
    }

    private static AutomatedFacility facility(CelestialObjectId bodyId, UUID teamId) {
        AutomatedFacility facility = unregisteredFacility(bodyId);
        CelestialAssetStore.registerAsset(teamId, facility);
        return facility;
    }

    private static AutomatedFacility unregisteredFacility(CelestialObjectId bodyId) {
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

    private static TileHammerCannon cannonWith(ItemStack stack) {
        TileHammerCannon cannon = new TileHammerCannon();
        InventoryBasic inventory = new InventoryBasic("test", false, 1);
        inventory.setInventorySlotContents(0, stack);
        cannon.getChestInventories()
            .add(inventory);
        return cannon;
    }

    private static ItemStack stack(int amount, String grade) {
        ItemStack stack = new ItemStack(Items.iron_ingot, amount, 0);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("grade", grade);
        stack.setTagCompound(tag);
        return stack;
    }

}
