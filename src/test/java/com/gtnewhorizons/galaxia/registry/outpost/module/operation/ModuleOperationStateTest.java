package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ModuleOperationStateTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void rejectsMalformedState() {
        ModuleOperationPlan plan = rebuildPlan(true, 3);

        assertThrows(
            IllegalStateException.class,
            () -> ModuleOperationState
                .restore(plan, ModuleOperationPhase.WAITING_FOR_MATERIALS, 1, Map.of(), Map.of()));

        assertThrows(
            IllegalStateException.class,
            () -> ModuleOperationState.restore(plan, ModuleOperationPhase.REFUNDING, 0, Map.of(), Map.of()));

        assertThrows(
            IllegalStateException.class,
            () -> ModuleOperationState.restore(
                plan,
                ModuleOperationPhase.COMPLETE,
                plan.buildTicks(),
                Map.of(),
                Map.of(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 2L)));

        assertThrows(
            IllegalArgumentException.class,
            () -> ModuleOperationState.restore(
                plan,
                ModuleOperationPhase.WAITING_FOR_MATERIALS,
                0,
                Collections.singletonMap(null, 1L),
                Map.of()));
    }

    @Test
    void reserveItemsFlagStaysStableAcrossTransitions() {
        ModuleOperationState waiting = ModuleOperationState.waiting(rebuildPlan(true, 2));
        ModuleOperationState building = waiting.beginBuilding();
        ModuleOperationState complete = building.tickBuilding()
            .tickBuilding();

        assertTrue(waiting.reserveItems());
        assertTrue(building.reserveItems());
        assertTrue(complete.reserveItems());
    }

    @Test
    void rejectsInvalidPhaseTransitions() {
        ModuleOperationState waiting = ModuleOperationState.waiting(rebuildPlan(false, 2));

        assertThrows(IllegalStateException.class, waiting::tickBuilding);

        ModuleOperationState complete = waiting.beginBuilding()
            .tickBuilding()
            .tickBuilding();

        assertThrows(IllegalStateException.class, complete::beginBuilding);
        assertThrows(IllegalStateException.class, complete::cancel);
    }

    @Test
    void buildingCompletesAfterConfiguredTicks() {
        ModuleOperationState state = ModuleOperationState.waiting(rebuildPlan(false, 3))
            .beginBuilding();

        state.tickBuilding();
        assertEquals(ModuleOperationPhase.BUILDING, state.phase());
        assertEquals(1, state.elapsedBuildTicks());

        state.tickBuilding();
        assertEquals(ModuleOperationPhase.BUILDING, state.phase());
        assertEquals(2, state.elapsedBuildTicks());

        state.tickBuilding();
        assertEquals(ModuleOperationPhase.COMPLETE, state.phase());
        assertEquals(3, state.elapsedBuildTicks());
    }

    @Test
    void cancelFromWaitingOrBuildingPreservesFullDepositIntent() {
        ModuleOperationState waitingEmpty = ModuleOperationState.waiting(rebuildPlan(true, 5));
        ModuleOperationState cancelledWithoutDeposit = waitingEmpty.cancel();

        assertEquals(ModuleOperationPhase.CANCELLED, cancelledWithoutDeposit.phase());
        assertTrue(
            cancelledWithoutDeposit.refundBuffer()
                .isEmpty());

        Map<ItemStackWrapper, Long> deposits = Map.of(
            ItemStackWrapper.of(new ItemStack(Items.iron_ingot)),
            3L,
            ItemStackWrapper.of(new ItemStack(Items.gold_ingot)),
            7L);

        ModuleOperationState waitingWithDeposit = ModuleOperationState.waiting(rebuildPlan(true, 5))
            .withDepositedResources(deposits);
        ModuleOperationState waitingRefunding = waitingWithDeposit.cancel();

        assertEquals(ModuleOperationPhase.REFUNDING, waitingRefunding.phase());
        assertEquals(deposits, waitingRefunding.refundBuffer());

        ModuleOperationState buildingWithDeposit = ModuleOperationState.waiting(rebuildPlan(true, 5))
            .withDepositedResources(deposits)
            .beginBuilding();
        ModuleOperationState buildingRefunding = buildingWithDeposit.cancel();

        assertEquals(ModuleOperationPhase.REFUNDING, buildingRefunding.phase());
        assertEquals(deposits, buildingRefunding.refundBuffer());
        assertFalse(
            buildingRefunding.refundBuffer()
                .isEmpty());
    }

    @Test
    void finishRefundingClearsRefundAndDepositState() {
        ModuleOperationState refunding = ModuleOperationState.waiting(rebuildPlan(true, 5))
            .withDepositedResources(Map.of(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 3L))
            .cancel();

        ModuleOperationState cancelled = refunding.finishRefunding();

        assertEquals(ModuleOperationPhase.CANCELLED, cancelled.phase());
        assertTrue(
            cancelled.depositedResources()
                .isEmpty());
        assertTrue(
            cancelled.refundBuffer()
                .isEmpty());
    }

    private static ModuleOperationPlan rebuildPlan(boolean reserveItems, int buildTicks) {
        return new ModuleOperationPlan(
            new IModuleOperation.Hammer(ModuleTier.IV, HammerVariant.BIG),
            buildTicks,
            Map.of(),
            reserveItems);
    }
}
