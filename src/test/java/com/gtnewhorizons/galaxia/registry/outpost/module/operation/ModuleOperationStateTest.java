package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

final class ModuleOperationStateTest {

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
            () -> ModuleOperationState
                .restore(plan, ModuleOperationPhase.COMPLETE, plan.buildTicks(), Map.of(), Map.of("ore:iron", 2L)));

        assertThrows(
            IllegalArgumentException.class,
            () -> ModuleOperationState
                .restore(plan, ModuleOperationPhase.WAITING_FOR_MATERIALS, 0, Map.of("", 1L), Map.of()));
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

        ModuleOperationState tick1 = state.tickBuilding();
        ModuleOperationState tick2 = tick1.tickBuilding();
        ModuleOperationState tick3 = tick2.tickBuilding();

        assertEquals(ModuleOperationPhase.BUILDING, tick1.phase());
        assertEquals(1, tick1.elapsedBuildTicks());
        assertEquals(ModuleOperationPhase.BUILDING, tick2.phase());
        assertEquals(2, tick2.elapsedBuildTicks());
        assertEquals(ModuleOperationPhase.COMPLETE, tick3.phase());
        assertEquals(3, tick3.elapsedBuildTicks());
    }

    @Test
    void cancelFromWaitingOrBuildingPreservesFullDepositIntent() {
        ModuleOperationState waitingEmpty = ModuleOperationState.waiting(rebuildPlan(true, 5));
        ModuleOperationState cancelledWithoutDeposit = waitingEmpty.cancel();

        assertEquals(ModuleOperationPhase.CANCELLED, cancelledWithoutDeposit.phase());
        assertTrue(
            cancelledWithoutDeposit.refundBuffer()
                .isEmpty());

        Map<String, Long> deposits = Map.of("plate.titanium", 3L, "circuit.advanced", 7L);

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

    private static ModuleOperationPlan rebuildPlan(boolean reserveItems, int buildTicks) {
        ModuleOperationTargetSpec target = new ModuleOperationTargetSpec(
            ModuleOperationKind.UPGRADE_REBUILD,
            FacilityModuleKind.HAMMER,
            ModuleTier.EV,
            FacilityModuleKind.HAMMER,
            ModuleTier.IV,
            "BIG");
        return new ModuleOperationPlan(target, buildTicks, 80, reserveItems);
    }
}
