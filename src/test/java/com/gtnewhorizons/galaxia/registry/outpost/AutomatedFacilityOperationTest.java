package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.BitSet;
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
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

import cpw.mods.fml.common.registry.GameData;

final class AutomatedFacilityOperationTest {

    private static final Item TEST_FILLER_ITEM = new Item();
    private static final Item TEST_REFUND_ITEM = new Item();

    @BeforeAll
    static void initRegistries() throws ReflectiveOperationException {
        registerTestItem(31000, "galaxia:test_filler_item", TEST_FILLER_ITEM);
        registerTestItem(31001, "galaxia:test_refund_item", TEST_REFUND_ITEM);
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
    void itemInventoryCapacityStartsAtBaseLimit() {
        AutomatedFacility facility = facilityWithHammer();

        assertEquals(1000L, facility.itemInventoryCapacity());
        assertEquals(1000L, facility.remainingItemInventoryCapacity());
    }

    @Test
    void storageModulesIncreaseItemInventoryCapacity() {
        AutomatedFacility facility = facilityWithStorage();
        ModuleInstance storage = facility.modules()
            .get(0);
        facility.stationLayout()
            .place(storage);

        assertEquals(2024L, facility.itemInventoryCapacity());
    }

    @Test
    void insertInventoryAcceptsOnlyRemainingCapacity() {
        AutomatedFacility facility = facilityWithHammer();
        ItemStackWrapper key = ItemStackWrapper.of(new ItemStack(new Item()));

        assertEquals(1000L, facility.insertInventory(key, 1200L));

        assertEquals(1000L, facility.inventory.getAmount(key));
        assertEquals(0L, facility.remainingItemInventoryCapacity());
    }

    @Test
    void refundFlushKeepsRemainderWhenInventoryIsFull() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStackWrapper filler = ItemStackWrapper.of(new ItemStack(TEST_FILLER_ITEM));
        ItemStackWrapper refund = ItemStackWrapper.of(new ItemStack(TEST_REFUND_ITEM));
        facility.insertInventory(filler, 998L);
        module.setOperation(
            ModuleOperationState
                .restore(plan(), ModuleOperationPhase.REFUNDING, 0, Map.of(), Map.of(refund.toKey(), 5L)));

        assertTrue(facility.flushModuleOperationRefund(module));

        assertEquals(2L, facility.inventory.getAmount(refund));
        assertNotNull(module.operationOrNull());
        assertEquals(
            ModuleOperationPhase.REFUNDING,
            module.operationOrNull()
                .phase());
        assertEquals(
            3L,
            module.operationOrNull()
                .refundBuffer()
                .get(refund.toKey()));
    }

    @Test
    void refundingModuleBlocksNextUpgradeUntilBufferEmpties() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStackWrapper filler = ItemStackWrapper.of(new ItemStack(TEST_FILLER_ITEM));
        ItemStackWrapper refund = ItemStackWrapper.of(new ItemStack(TEST_REFUND_ITEM));
        facility.insertInventory(filler, 1000L);
        module.setOperation(
            ModuleOperationState
                .restore(plan(), ModuleOperationPhase.REFUNDING, 0, Map.of(), Map.of(refund.toKey(), 1L)));

        facility.tick();

        assertEquals(
            ModuleOperationPhase.REFUNDING,
            module.operationOrNull()
                .phase());

        facility.inventory.add(filler, -1L);
        facility.tick();

        assertEquals(
            ModuleOperationPhase.CANCELLED,
            module.operationOrNull()
                .phase());
        assertEquals(1L, facility.inventory.getAmount(refund));
    }

    @Test
    void completedRefundTricklesThroughRefundBufferWhenInventoryHasLimitedSpace() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStackWrapper filler = ItemStackWrapper.of(new ItemStack(TEST_FILLER_ITEM));
        ItemStackWrapper refund = ItemStackWrapper.of(new ItemStack(TEST_REFUND_ITEM));
        facility.insertInventory(filler, 999L);
        module.setOperation(
            ModuleOperationState.waiting(hammerUpgradePlan(2, false, refund))
                .beginBuilding());

        facility.tick();
        facility.tick();

        assertEquals(ModuleTier.LuV, module.tier());
        assertEquals(
            ModuleOperationPhase.REFUNDING,
            module.operationOrNull()
                .phase());

        facility.tick();

        assertEquals(1L, facility.inventory.getAmount(refund));
        assertEquals(
            5L,
            module.operationOrNull()
                .refundBuffer()
                .get(refund.toKey()));
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
    void tickCompletesMinerFocusTierOperationAndResetsAlignment() {
        AutomatedFacility facility = facilityWithMiner();
        ModuleInstance module = facility.modules()
            .get(0);
        ModuleMiner miner = (ModuleMiner) module.component();
        miner.setFocus(MinerFocusTier.I, "ore:iron", 1200);
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

    @Test
    void tickCompletesMinerFocusTierOperationWithoutSelectedOre() {
        AutomatedFacility facility = facilityWithMiner();
        ModuleInstance module = facility.modules()
            .get(0);
        ModuleMiner miner = (ModuleMiner) module.component();
        module.setOperation(
            ModuleOperationState
                .waiting(
                    new ModuleOperationPlan(
                        new MinerFocusOperation(ModuleTier.EV, MinerFocusTier.II.name(), null),
                        2,
                        Map.of(),
                        false,
                        true))
                .beginBuilding());

        facility.tick();
        facility.tick();

        assertNull(module.operationOrNull());
        assertEquals(MinerFocusTier.II, miner.focusTier());
        assertNull(miner.focusOreKeyOrNull());
        assertEquals(0, miner.focusAlignmentProgress());
    }

    @Test
    void tickCompletesGenericTierOperation() {
        AutomatedFacility facility = facilityWithStorage();
        ModuleInstance module = facility.modules()
            .get(0);
        module.setOperation(
            ModuleOperationState.waiting(tierOperationPlan(2, ModuleTier.EV))
                .beginBuilding());

        facility.tick();
        facility.tick();

        assertNull(module.operationOrNull());
        assertEquals(ModuleTier.EV, module.tier());
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

    private static AutomatedFacility facilityWithStorage() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance module = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
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

    private static ModuleOperationPlan tierOperationPlan(int buildTicks, ModuleTier targetTier) {
        return new ModuleOperationPlan(new ModuleTierOperation(targetTier), buildTicks, Map.of(), false, true);
    }

    private static ItemStack material() {
        return new ItemStack(new Item());
    }

    private static void registerTestItem(int id, String key, Item item) throws ReflectiveOperationException {
        Object registry = GameData.getItemRegistry();
        for (Method method : registry.getClass()
            .getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 4 && parameters[0] == int.class
                && parameters[1] == String.class
                && parameters[2].isAssignableFrom(Item.class)
                && parameters[3] == BitSet.class) {
                method.setAccessible(true);
                method.invoke(registry, id, key, item, new BitSet());
                return;
            }
        }
        throw new NoSuchMethodException("Item registry raw add method");
    }

}
