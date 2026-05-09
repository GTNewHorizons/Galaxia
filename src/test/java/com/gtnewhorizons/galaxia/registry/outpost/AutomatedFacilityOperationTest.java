package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.MinerFocusOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class AutomatedFacilityOperationTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void reserveOperationMaterialsMovesItemsFromInventoryToDeposit() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStack material = material();
        ItemStackWrapper key = ItemStackWrapper.of(material);
        facility.inventory.add(key, 8L);
        module.setOperation(ModuleOperationState.waiting(plan()));

        assertTrue(facility.tryReserveOperationMaterials(module, Map.of(key, 5L)));

        assertEquals(3L, facility.inventory.getAmount(key));
        assertEquals(
            5L,
            module.operationOrNull()
                .depositedResources()
                .get(key.toKey()));
    }

    @Test
    void reserveOperationMaterialsIsAtomicWhenInventoryIsShort() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStack material = material();
        ItemStackWrapper key = ItemStackWrapper.of(material);
        facility.inventory.add(key, 2L);
        module.setOperation(ModuleOperationState.waiting(plan()));

        assertFalse(facility.tryReserveOperationMaterials(module, Map.of(key, 5L)));

        assertEquals(2L, facility.inventory.getAmount(key));
        assertTrue(
            module.operationOrNull()
                .depositedResources()
                .isEmpty());
    }

    @Test
    void reserveAvailableOperationMaterialsCollectsPartialDeposits() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStack material = material();
        ItemStackWrapper key = ItemStackWrapper.of(material);
        facility.inventory.add(key, 2L);
        module.setOperation(ModuleOperationState.waiting(plan()));

        assertFalse(facility.tryReserveAvailableOperationMaterials(module, Map.of(key, 5L)));

        assertEquals(0L, facility.inventory.getAmount(key));
        assertEquals(
            2L,
            module.operationOrNull()
                .depositedResources()
                .get(key.toKey()));

        facility.inventory.add(key, 3L);

        assertTrue(facility.tryReserveAvailableOperationMaterials(module, Map.of(key, 5L)));
        assertEquals(
            5L,
            module.operationOrNull()
                .depositedResources()
                .get(key.toKey()));
    }

    @Test
    void cancelQueuesFullDepositIntoRefundBuffer() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStack material = material();
        ItemStackWrapper key = ItemStackWrapper.of(material);
        facility.inventory.add(key, 8L);
        module.setOperation(ModuleOperationState.waiting(plan()));
        facility.tryReserveOperationMaterials(module, Map.of(key, 5L));

        facility.cancelModuleOperation(module);

        assertEquals(
            ModuleOperationPhase.REFUNDING,
            module.operationOrNull()
                .phase());
        assertEquals(3L, facility.inventory.getAmount(key));
        assertEquals(
            5L,
            module.operationOrNull()
                .refundBuffer()
                .get(key.toKey()));
    }

    @Test
    void flushModuleOperationRefundCrashesOnUnresolvableItemKey() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStack material = material();
        ItemStackWrapper key = ItemStackWrapper.of(material);
        facility.inventory.add(key, 8L);
        module.setOperation(ModuleOperationState.waiting(plan()));
        facility.tryReserveOperationMaterials(module, Map.of(key, 5L));
        facility.cancelModuleOperation(module);

        assertThrows(IllegalStateException.class, () -> facility.flushModuleOperationRefund(module));
    }

    @Test
    void reserveOperationMaterialsRejectsMalformedState() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);

        assertThrows(
            IllegalStateException.class,
            () -> facility.tryReserveOperationMaterials(module, Map.of(ItemStackWrapper.of(material()), 1L)));

        module.setOperation(
            ModuleOperationState.waiting(plan())
                .beginBuilding());

        assertThrows(
            IllegalStateException.class,
            () -> facility.tryReserveOperationMaterials(module, Map.of(ItemStackWrapper.of(material()), 1L)));
    }

    @Test
    void tickCompletesHammerUpgradeAndAppliesTargetSpec() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        module.setOperation(
            ModuleOperationState.waiting(hammerUpgradePlan(2, true))
                .beginBuilding());

        assertEquals(ModuleTier.EV, module.tier());

        facility.tick();
        facility.tick();

        assertNull(module.operationOrNull());
        assertEquals(ModuleTier.LuV, module.tier());
        assertEquals(HammerVariant.BIG, ((ModuleHammer) module.component()).variant());
    }

    @Test
    void completedHammerUpgradeRefundUsesReplacedTierCost() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStackWrapper material = ItemStackWrapper.of(material());
        module.setOperation(
            ModuleOperationState.waiting(hammerUpgradePlan(2, false, material))
                .beginBuilding());

        facility.tick();
        facility.tick();

        ModuleOperationState operation = module.operationOrNull();
        assertNotNull(operation);
        assertEquals(ModuleOperationPhase.REFUNDING, operation.phase());
        assertEquals(
            6L,
            operation.refundBuffer()
                .get(material.toKey()));
    }

    @Test
    void completedHammerUpgradeRefundUsesPlanBuildTicks() throws Exception {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStackWrapper material = ItemStackWrapper.of(material());
        module.setOperation(
            ModuleOperationState.waiting(hammerUpgradePlan(2, false, material))
                .beginBuilding());

        facility.tick();
        facility.tick();

        ModuleOperationState operation = module.operationOrNull();
        assertNotNull(operation);
        Method isCompletionRefund = AutomatedFacility.class
            .getDeclaredMethod("isCompletionRefund", ModuleOperationState.class);
        isCompletionRefund.setAccessible(true);
        assertTrue((boolean) isCompletionRefund.invoke(null, operation));
    }

    @Test
    void tickCompletesMinerFocusOperationAndResetsAlignment() {
        AutomatedFacility facility = facilityWithMiner();
        ModuleInstance module = facility.modules()
            .get(0);
        ModuleMiner miner = (ModuleMiner) module.component();
        module.setOperation(
            ModuleOperationState.waiting(minerFocusPlan(2))
                .beginBuilding());

        facility.tick();
        facility.tick();

        assertNull(module.operationOrNull());
        assertEquals(MinerFocusTier.II, miner.focusTier());
        assertEquals("ore:iron", miner.focusOreKeyOrNull());
        assertEquals(0, miner.focusAlignmentProgress());
    }

    private static AutomatedFacility facilityWithHammer() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance module = FacilityModuleKind.HAMMER
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.EV);
        facility.addModule(module);
        return facility;
    }

    private static AutomatedFacility facilityWithMiner() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        ModuleInstance module = FacilityModuleKind.MINER
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.EV);
        facility.addModule(module);
        return facility;
    }

    private static ModuleOperationPlan plan() {
        return hammerUpgradePlan(200);
    }

    private static ModuleOperationPlan hammerUpgradePlan(int buildTicks) {
        return hammerUpgradePlan(buildTicks, false);
    }

    private static ModuleOperationPlan hammerUpgradePlan(int buildTicks, boolean voidCompletionRefund) {
        return hammerUpgradePlan(buildTicks, voidCompletionRefund, ItemStackWrapper.of(material()));
    }

    private static ModuleOperationPlan hammerUpgradePlan(int buildTicks, boolean voidCompletionRefund,
        ItemStackWrapper material) {
        return new ModuleOperationPlan(
            new HammerModuleOperation(ModuleTier.LuV, HammerVariant.BIG.name()),
            buildTicks,
            Map.of(material, 128L),
            Map.of(material, 8L),
            80,
            false,
            voidCompletionRefund);
    }

    private static ModuleOperationPlan minerFocusPlan(int buildTicks) {
        return new ModuleOperationPlan(
            new MinerFocusOperation(ModuleTier.EV, MinerFocusTier.II.name(), "ore:iron"),
            buildTicks,
            Map.of(),
            false,
            true);
    }

    private static ItemStack material() {
        return new ItemStack(new Item());
    }
}
