package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class CraterDepositsTest {

    @Test
    void lastPortionCannotBeIssuedToTwoExtractorsAndFailureDoesNotDepleteIt() {
        CraterDeposits deposits = new CraterDeposits();
        deposits.register("crater", 15, 20, 48, CraterSize.MEDIUM, CraterProfile.IRON_SILICA, 1);
        var first = deposits.begin("crater", false, false);
        assertNotNull(first);
        assertNull(deposits.begin("crater", true, false));
        assertEquals(
            1,
            deposits.inspect("crater")
                .remainingReserve());
        assertTrue(deposits.finish(first.id(), false));
        var retry = deposits.begin("crater", true, false);
        assertNotNull(retry);
        assertTrue(deposits.finish(retry.id(), true));
        assertFalse(deposits.finish(retry.id(), true));
        assertEquals(
            0,
            deposits.inspect("crater")
                .remainingReserve());
    }

    @Test
    void allExtractorsShareCompletedFrackingHistory() {
        CraterDeposits deposits = new CraterDeposits();
        deposits.register("crater", 0, 0, 16, CraterSize.SMALL, CraterProfile.SCANDIUM_BAUXITE, 1);
        deposits.finish(
            deposits.begin("crater", false, false)
                .id(),
            true);
        assertNull(deposits.begin("crater", false, false));
        var first = deposits.begin("crater", true, false);
        var second = deposits.begin("crater", true, false);
        assertEquals(16000, first.drillingFluid());
        assertEquals(16000, second.drillingFluid());
        deposits.finish(first.id(), true);
        deposits.finish(second.id(), false);
        assertEquals(
            1,
            deposits.inspect("crater")
                .completedFracking());
        long nextCost = deposits.begin("crater", true, false)
            .drillingFluid();
        assertTrue(nextCost > 16000);
        assertEquals(
            deposits.inspect("crater")
                .nextFrackingFluid(),
            nextCost);
    }

    @Test
    void geometryAndPendingOperationSurviveSaveLoadWithoutRerolling() {
        CraterDeposits deposits = new CraterDeposits();
        deposits.register("crater", 15, 20, 48, CraterSize.MEDIUM, CraterProfile.GOLD_COBALT, 3000);
        assertEquals("crater", deposits.find(35, 20));
        assertNull(deposits.find(40, 20));
        var pending = deposits.begin("crater", false, true);
        NBTTagCompound nbt = new NBTTagCompound();
        deposits.writeToNBT(nbt);
        CraterDeposits restored = new CraterDeposits();
        restored.readFromNBT(nbt);
        assertEquals(pending, restored.operation(pending.id()));
        assertEquals("crater", restored.find(-5, 20));
        var snapshot = restored.inspect("crater");
        assertEquals("crater", snapshot.id());
        assertEquals(15, snapshot.x());
        assertEquals(20, snapshot.z());
        assertEquals(48, snapshot.diameter());
        assertTrue(restored.finish(pending.id(), true));
        assertEquals(
            2998,
            restored.inspect("crater")
                .remainingReserve());
        restored.register("crater", 15, 20, 48, CraterSize.MEDIUM, CraterProfile.GOLD_COBALT, 3000);
        assertEquals(
            2998,
            restored.inspect("crater")
                .remainingReserve());
    }
}
