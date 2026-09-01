package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class FacilityInventoryContractTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void rejectedItemCannotBeInsertedButRemainsVisibleAndExtractable() {
        AutomatedFacility facility = facility();
        ItemStackWrapper stored = ItemStackWrapper.of(new ItemStack(Items.stick));
        ItemStackWrapper accepted = ItemStackWrapper.of(new ItemStack(Items.diamond));
        facility.loadFromSnapshot(Map.of(stored, 4L));
        facility.addFilter(
            accepted.toItemStack()
                .getUnlocalizedName(),
            true);

        assertEquals(0L, facility.insert(stored, 2L));
        assertEquals(4L, facility.itemAmount(stored));
        assertEquals(2L, facility.extract(stored, 2L));
        assertEquals(2L, facility.itemAmount(stored));
    }

    @Test
    void itemTransfersReportExactlyTheAppliedStateChange() {
        AutomatedFacility facility = facility();
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));

        assertEquals(7L, facility.insert(item, 7L));
        assertEquals(7L, facility.itemAmount(item));
        assertEquals(5L, facility.extract(item, 5L));
        assertEquals(2L, facility.itemAmount(item));
        assertEquals(2L, facility.extract(item, 5L));
        assertEquals(0L, facility.itemAmount(item));
    }

    @Test
    void fluidTransfersReportExactlyTheAppliedStateChange() {
        AutomatedFacility facility = facility();
        FluidKey fluid = new FluidKey(FluidRegistry.WATER, null);

        assertEquals(4_096L, facility.insert(fluid, 4_096L));
        assertEquals(4_096L, facility.fluidAmount(fluid));
        assertEquals(1_024L, facility.extract(fluid, 1_024L));
        assertEquals(3_072L, facility.fluidAmount(fluid));
        assertEquals(3_072L, facility.extract(fluid, 8_192L));
        assertEquals(0L, facility.fluidAmount(fluid));
    }

    @Test
    void negativeTransferRequestsAreRejectedWithoutChangingState() {
        AutomatedFacility facility = facility();
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.gold_ingot));

        assertThrows(IllegalArgumentException.class, () -> facility.insert(item, -1L));
        assertThrows(IllegalArgumentException.class, () -> facility.extract(item, -1L));
        assertEquals(0L, facility.itemAmount(item));
        assertEquals(0, facility.getStateRevision());
    }

    @Test
    void changedTransfersAdvanceRevisionOnceAndNoOpsDoNot() {
        AutomatedFacility facility = facility();
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.diamond));

        assertEquals(0L, facility.insert(item, 0L));
        assertEquals(0L, facility.extract(item, 1L));
        assertEquals(0, facility.getStateRevision());

        assertEquals(2L, facility.insert(item, 2L));
        assertEquals(1, facility.getStateRevision());
        assertEquals(1L, facility.extract(item, 1L));
        assertEquals(2, facility.getStateRevision());
    }

    @Test
    void acceptedExchangeUsesConsumedItemCapacityAndAdvancesRevisionOnce() {
        AutomatedFacility facility = facility();
        ItemStackWrapper input = ItemStackWrapper.of(new ItemStack(Items.diamond));
        ItemStackWrapper output = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        long capacity = facility.itemCapacity();
        facility.insert(input, capacity);
        int revisionBefore = facility.getStateRevision();

        assertTrue(
            facility.tryExchange(new InventoryExchange(Map.of(input, 10L), Map.of(), Map.of(output, 10L), Map.of())));

        assertEquals(capacity - 10L, facility.itemAmount(input));
        assertEquals(10L, facility.itemAmount(output));
        assertEquals(revisionBefore + 1, facility.getStateRevision());
    }

    @Test
    void netZeroExchangeIsAcceptedWithoutChangingInventoryOrRevision() {
        AutomatedFacility facility = facility();
        ItemStackWrapper catalyst = ItemStackWrapper.of(new ItemStack(Items.diamond));
        facility.insert(catalyst, 1L);
        int revisionBefore = facility.getStateRevision();

        assertTrue(
            facility
                .tryExchange(new InventoryExchange(Map.of(catalyst, 1L), Map.of(), Map.of(catalyst, 1L), Map.of())));

        assertEquals(1L, facility.itemAmount(catalyst));
        assertEquals(revisionBefore, facility.getStateRevision());
    }

    @Test
    void rejectedExchangeLeavesInputsOutputsAndRevisionUnchanged() {
        AutomatedFacility facility = facility();
        ItemStackWrapper input = ItemStackWrapper.of(new ItemStack(Items.diamond));
        ItemStackWrapper blockedOutput = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        ItemStackWrapper acceptedOutput = ItemStackWrapper.of(new ItemStack(Items.gold_ingot));
        facility.insert(input, 4L);
        facility.addFilter(
            acceptedOutput.toItemStack()
                .getUnlocalizedName(),
            true);
        int revisionBefore = facility.getStateRevision();

        assertFalse(
            facility
                .tryExchange(new InventoryExchange(Map.of(input, 4L), Map.of(), Map.of(blockedOutput, 2L), Map.of())));

        assertEquals(4L, facility.itemAmount(input));
        assertEquals(0L, facility.itemAmount(blockedOutput));
        assertEquals(revisionBefore, facility.getStateRevision());
    }

    @Test
    void aggregateItemAmountSaturatesWhileOverCapacityInventoryRecovers() {
        AutomatedFacility facility = facility();
        ItemStackWrapper first = ItemStackWrapper.of(new ItemStack(Items.diamond));
        ItemStackWrapper second = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        facility.loadFromSnapshot(Map.of(first, Long.MAX_VALUE, second, 1L));

        assertEquals(Long.MAX_VALUE, facility.storedItemAmount());
        assertEquals(0L, facility.remainingItemCapacity());
        assertEquals(1L, facility.extract(second, 1L));
        assertEquals(Long.MAX_VALUE, facility.storedItemAmount());
    }

    @Test
    void rejectedItemSnapshotLeavesExistingItemsUntouched() {
        AutomatedFacility facility = facility();
        ItemStackWrapper existing = ItemStackWrapper.of(new ItemStack(Items.diamond));
        ItemStackWrapper replacement = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        facility.loadFromSnapshot(Map.of(existing, 3L));
        Map<ItemStackWrapper, Long> invalid = new LinkedHashMap<>();
        invalid.put(replacement, 2L);
        invalid.put(null, 1L);

        assertThrows(IllegalArgumentException.class, () -> facility.loadFromSnapshot(invalid));

        assertEquals(Map.of(existing, 3L), facility.itemSnapshot());
    }

    @Test
    void rejectedFluidSnapshotLeavesExistingFluidsUntouched() {
        AutomatedFacility facility = facility();
        FluidKey existing = new FluidKey(FluidRegistry.WATER, null);
        facility.loadFluidSnapshot(
            Map.of(
                existing.fluid()
                    .getName(),
                3L));
        Map<String, Long> invalid = new LinkedHashMap<>();
        invalid.put(FluidRegistry.LAVA.getName(), 2L);
        invalid.put("galaxia.missing_fluid", 1L);

        assertThrows(IllegalArgumentException.class, () -> facility.loadFluidSnapshot(invalid));

        assertEquals(3L, facility.fluidAmount(existing));
        assertEquals(0L, facility.fluidAmount(new FluidKey(FluidRegistry.LAVA, null)));
    }

    @Test
    void rejectedFilterReplacementLeavesSelectedSideUntouched() {
        AutomatedFacility facility = facility();
        facility.setFilters(List.of("item.existing"), true);
        List<String> invalidItems = new ArrayList<>();
        invalidItems.add("item.replacement");
        invalidItems.add(null);

        assertThrows(IllegalArgumentException.class, () -> facility.setFilters(invalidItems, true));
        assertThrows(
            IllegalArgumentException.class,
            () -> facility.setFilters(List.of("galaxia.missing_fluid"), false));

        assertEquals(
            List.of("item.existing"),
            facility.filtersSnapshot()
                .get(true));
        assertFalse(
            facility.filtersSnapshot()
                .containsKey(false));
    }

    @Test
    void clearRemovesInventoryAmountsAndBothFilterSides() {
        AutomatedFacility facility = facility();
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.diamond));
        FluidKey fluid = new FluidKey(FluidRegistry.WATER, null);
        facility.insert(item, 2L);
        facility.insert(fluid, 3L);
        facility.addFilter(
            item.toItemStack()
                .getUnlocalizedName(),
            true);
        facility.addFilter(
            fluid.fluid()
                .getName(),
            false);

        facility.clear();

        assertEquals(0L, facility.itemAmount(item));
        assertEquals(0L, facility.fluidAmount(fluid));
        assertEquals(Map.of(), facility.filtersSnapshot());
    }

    @Test
    void batchMaterialReservationMutatesInventoryAndModuleWithOneRevision() {
        AutomatedFacility facility = facility();
        ItemStackWrapper iron = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        ItemStackWrapper gold = ItemStackWrapper.of(new ItemStack(Items.gold_ingot));
        Map<ItemStackWrapper, Long> materialCost = Map.of(iron, 2L, gold, 3L);
        facility.insert(iron, 2L);
        facility.insert(gold, 3L);
        ModuleInstance module = operationModule(materialCost);
        int revisionBefore = facility.getStateRevision();

        assertTrue(facility.tryReserveOperationMaterials(module, materialCost));

        assertEquals(0L, facility.itemAmount(iron));
        assertEquals(0L, facility.itemAmount(gold));
        assertEquals(
            2L,
            module.operationOrNull()
                .depositedResources()
                .get(iron.toKey()));
        assertEquals(
            3L,
            module.operationOrNull()
                .depositedResources()
                .get(gold.toKey()));
        assertEquals(revisionBefore + 1, facility.getStateRevision());
    }

    @Test
    void freeOperationReservationDoesNotRewriteModuleOrAdvanceRevision() {
        AutomatedFacility facility = facility();
        ModuleInstance module = operationModule(Map.of());
        ModuleOperationState operationBefore = module.operationOrNull();
        int revisionBefore = facility.getStateRevision();

        assertTrue(facility.tryReserveOperationMaterials(module, Map.of()));

        assertEquals(operationBefore, module.operationOrNull());
        assertEquals(revisionBefore, facility.getStateRevision());
    }

    @Test
    void refundBufferRetainsPartialRemainderUntilCapacityBecomesAvailable() {
        AutomatedFacility facility = facility();
        ItemStackWrapper filler = ItemStackWrapper.of(new ItemStack(Items.stick));
        ItemStackWrapper refund = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        facility.insert(filler, facility.itemCapacity());
        ModuleInstance module = operationModule(Map.of(refund, 2L));
        module.setOperation(
            module.operationOrNull()
                .withDepositedResources(Map.of(refund.toKey(), 2L))
                .cancel());

        assertFalse(facility.flushModuleOperationRefund(module));
        assertEquals(
            2L,
            module.operationOrNull()
                .refundBuffer()
                .get(refund.toKey()));

        facility.extract(filler, 1L);
        assertTrue(facility.flushModuleOperationRefund(module));
        assertEquals(1L, facility.itemAmount(refund));
        assertEquals(
            1L,
            module.operationOrNull()
                .refundBuffer()
                .get(refund.toKey()));

        facility.extract(filler, 1L);
        assertTrue(facility.flushModuleOperationRefund(module));
        assertEquals(2L, facility.itemAmount(refund));
        assertEquals(
            ModuleOperationPhase.CANCELLED,
            module.operationOrNull()
                .phase());
    }

    @Test
    void itemInsertionStopsAtFiniteLayoutDerivedCapacity() {
        AutomatedFacility facility = facility();
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.redstone));
        long capacity = facility.itemCapacity();

        assertEquals(capacity, facility.insert(item, capacity + 20L));
        assertEquals(capacity, facility.itemAmount(item));
        assertEquals(0L, facility.insert(item, 1L));
    }

    @Test
    void fluidInsertionIsNotLimitedByFiniteItemCapacity() {
        AutomatedFacility facility = facility();
        FluidKey fluid = new FluidKey(FluidRegistry.LAVA, null);
        long requested = facility.itemCapacity() * 4L;

        assertEquals(requested, facility.insert(fluid, requested));
        assertEquals(requested, facility.fluidAmount(fluid));
    }

    @Test
    void fluidInsertionAppliesOnlyTheRepresentableRemainder() {
        AutomatedFacility facility = facility();
        FluidKey fluid = new FluidKey(FluidRegistry.WATER, null);
        facility.loadFluidSnapshot(
            Map.of(
                fluid.fluid()
                    .getName(),
                Long.MAX_VALUE - 5L));

        assertEquals(5L, facility.insert(fluid, 10L));
        assertEquals(Long.MAX_VALUE, facility.fluidAmount(fluid));
        assertEquals(1, facility.getStateRevision());
        assertEquals(0L, facility.insert(fluid, 1L));
        assertEquals(1, facility.getStateRevision());
    }

    @Test
    void unchangedFilterMutationsDoNotAdvanceRevision() {
        AutomatedFacility facility = facility();
        String accepted = ItemStackWrapper.of(new ItemStack(Items.diamond))
            .toItemStack()
            .getUnlocalizedName();

        facility.clearFilters(true);
        facility.removeFilter(accepted, true);
        assertEquals(0, facility.getStateRevision());

        facility.addFilter(accepted, true);
        assertEquals(1, facility.getStateRevision());
        facility.addFilter(accepted, true);
        facility.removeFilter("item.absent", true);
        facility.setFilters(List.of(accepted), true);
        assertEquals(1, facility.getStateRevision());

        facility.clearFilters(true);
        assertEquals(2, facility.getStateRevision());
        facility.clearFilters(true);
        assertEquals(2, facility.getStateRevision());
    }

    @Test
    void overCapacityInventoryBlocksInsertionButAllowsExtractionRecovery() {
        AutomatedFacility facility = facility();
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.stick));
        long capacity = facility.itemCapacity();
        facility.loadFromSnapshot(Map.of(item, capacity + 100L));

        assertEquals(0L, facility.insert(item, 1L));
        assertEquals(50L, facility.extract(item, 50L));
        assertEquals(capacity + 50L, facility.itemAmount(item));
        assertEquals(51L, facility.extract(item, 51L));
        assertEquals(capacity - 1L, facility.itemAmount(item));
        assertEquals(1L, facility.insert(item, 1L));
        assertEquals(capacity, facility.itemAmount(item));
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance operationModule(Map<ItemStackWrapper, Long> materialCost) {
        ModuleInstance module = new ModuleInstance(
            ModuleInstance.ID.create(),
            FacilityModuleRegistry.get(FacilityModuleKind.POWER),
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.NONE);
        module.setOperation(
            ModuleOperationState.waiting(
                new ModuleOperationPlan(new ModuleTierOperation(ModuleTier.IV), 1, materialCost, true, false)));
        return module;
    }
}
