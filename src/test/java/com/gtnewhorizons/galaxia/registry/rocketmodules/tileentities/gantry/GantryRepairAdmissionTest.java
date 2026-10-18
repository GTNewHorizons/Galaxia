package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler.Operation;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler.SupplyLimits;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler.WorkRequest;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

/** Product contract: exact repair requests choose supplied connected machines and reserve once. */
final class GantryRepairAdmissionTest {

    @BeforeAll
    static void bootstrap() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    @Test
    void emptyFirstAssemblerIsSkippedAndChosenMachineReservesOnlyThisModuleBill() {
        UUID team = UUID.randomUUID();
        var origin = new TileEntityGantryTerminal();
        var junction = new TileEntityGantry();
        var firstTerminal = new TileEntityGantryTerminal();
        var secondTerminal = new TileEntityGantryTerminal();
        var empty = new FormedAssembler(team);
        var supplied = new FormedAssembler(team);
        empty.xCoord = 1;
        supplied.xCoord = 2;
        firstTerminal.connectAssembler(empty);
        secondTerminal.connectAssembler(supplied);
        empty.setGantryTerminal(firstTerminal);
        supplied.setGantryTerminal(secondTerminal);
        connect(origin, junction);
        connect(junction, firstTerminal);
        connect(junction, secondTerminal);
        supplied.setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 8));
        var request = new WorkRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            Operation.REPAIR,
            Map.of(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 4L),
            Map.of(),
            10,
            3);

        var accepted = GantryAPI.requestWork(origin, team, request);

        assertEquals(supplied.controllerId(), accepted.controllerId());
        assertEquals(request, supplied.acceptedWork());
        assertNull(empty.acceptedWork());
        assertEquals(4, supplied.getStackInSlot(0).stackSize);
        empty.setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 8));
        assertEquals(accepted, GantryAPI.requestWork(origin, team, request));
        assertNull(empty.acceptedWork());
        assertEquals(8, empty.getStackInSlot(0).stackSize);
        assertEquals(4, supplied.getStackInSlot(0).stackSize);
    }

    private static void connect(TileEntityGantry first, TileEntityGantry second) {
        first.neighbours.add(second);
        second.neighbours.add(first);
    }

    private static final class FormedAssembler extends TileEntityModuleAssembler {

        private FormedAssembler(UUID team) {
            super(new SupplyLimits(2, 1, 1000, 1000, 100));
            structureValid = true;
            machineIdentity().claim(team);
        }
    }
}
