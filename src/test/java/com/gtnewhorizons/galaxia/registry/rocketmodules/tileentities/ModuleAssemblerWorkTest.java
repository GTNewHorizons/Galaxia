package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler.Admission;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler.Operation;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler.SupplyLimits;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler.WorkRequest;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import sun.misc.Unsafe;

/** Integration contracts for exact paid machine admission and durable claims. */
final class ModuleAssemblerWorkTest {

    private static final SupplyLimits LIMITS = new SupplyLimits(2, 1, 1000, 1000, 100);
    private static final UUID TEAM = UUID.randomUUID();
    private final Rocket.Registry registry = new Rocket.Registry();

    @BeforeAll
    static void bootstrap() {
        GalaxiaTestBootstrap.ensureMinecraft();
        TileEntity.addMapping(FormedAssembler.class, "test.galaxia.paid_module_assembler");
    }

    @Test
    void missingFluidDoesNotPartiallyChargeItems() {
        FormedAssembler machine = new FormedAssembler();
        machine.setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 4));

        assertEquals(Admission.MISSING_MATERIALS, machine.tryAccept(TEAM, request()));
        assertEquals(4, machine.getStackInSlot(0).stackSize);
        assertNull(machine.acceptedWork());
    }

    @Test
    void acceptanceReservesWholeBillAndCannotBeChargedAgainOrMixedWithAnotherVehicle() {
        FormedAssembler machine = supplied();
        WorkRequest request = request();

        assertEquals(Admission.ACCEPTED, machine.tryAccept(TEAM, request));
        assertEquals(2, machine.getStackInSlot(0).stackSize);
        assertEquals(100, machine.drain(ForgeDirection.UNKNOWN, 1000, false).amount);
        assertEquals(Admission.ALREADY_ACCEPTED, machine.tryAccept(TEAM, request));
        assertEquals(2, machine.getStackInSlot(0).stackSize);
        WorkRequest conflicting = new WorkRequest(
            request.workId(),
            UUID.randomUUID(),
            request.moduleId(),
            request.definitionId(),
            request.operation(),
            request.items(),
            request.fluids(),
            request.euPerTick(),
            request.workTicks());
        assertEquals(Admission.CONFLICT, machine.tryAccept(TEAM, conflicting));
        assertEquals(request, machine.acceptedWork());
        assertEquals(Admission.BUSY, machine.tryAccept(TEAM, request()));
        assertTrue(machine.receiveAssignedModule(request.workId(), request.vehicleId(), request.moduleId()));
        assertEquals(2, machine.getStackInSlot(0).stackSize);
    }

    @Test
    void reloadPreservesMachineIdentityPaidRequestAndRemainingWork() {
        FormedAssembler machine = supplied();
        WorkRequest request = request();
        assertEquals(Admission.ACCEPTED, machine.tryAccept(TEAM, request));
        NBTTagCompound saved = new NBTTagCompound();
        machine.writeToNBT(saved);

        FormedAssembler restored = new FormedAssembler();
        restored.readFromNBT(saved);

        assertEquals(machine.controllerId(), restored.controllerId());
        assertEquals(request, restored.acceptedWork());
        assertEquals(request.workTicks(), restored.getAssemblyTicksRemaining());
        assertEquals(2, restored.getStackInSlot(0).stackSize);
        assertEquals(Admission.ALREADY_ACCEPTED, restored.tryAccept(TEAM, request));
        assertNull(restored.completedOutput());
    }

    @Test
    void energyAcceptanceReportsOnlyCompletePacketsThatFit() {
        FormedAssembler machine = new FormedAssembler();
        assertEquals(9, machine.injectEnergyUnits(ForgeDirection.NORTH, 100, 9));
        assertEquals(1, machine.injectEnergyUnits(ForgeDirection.NORTH, 100, 9));
        assertEquals(0, machine.injectEnergyUnits(ForgeDirection.NORTH, 100, 1));
        assertEquals(1000, machine.storedEU());
        assertEquals(0, new FormedAssembler().injectEnergyUnits(ForgeDirection.NORTH, 101, 1));
    }

    @Test
    void anotherTeamCannotReserveSuppliedMaterials() {
        FormedAssembler machine = supplied();
        assertEquals(Admission.WRONG_TEAM, machine.tryAccept(UUID.randomUUID(), request()));
        assertEquals(4, machine.getStackInSlot(0).stackSize);
        assertEquals(200, machine.drain(ForgeDirection.UNKNOWN, 1000, false).amount);
        assertNull(machine.acceptedWork());
    }

    @Test
    void repairWaitsForAssignedModuleAndEuThenHoldsOutputAcrossReloadUntilAcknowledged() throws Exception {
        FormedAssembler machine = supplied();
        machine.attachServerWorld();
        WorkRequest request = request();
        assertEquals(Admission.ACCEPTED, machine.tryAccept(TEAM, request));
        machine.injectEnergyUnits(ForgeDirection.NORTH, request.euPerTick(), request.workTicks());
        machine.tickWork(registry);
        assertEquals(request.workTicks(), machine.getAssemblyTicksRemaining());
        assertFalse(machine.receiveAssignedModule(request.workId(), UUID.randomUUID(), request.moduleId()));
        assertTrue(machine.receiveAssignedModule(request.workId(), request.vehicleId(), request.moduleId()));
        machine.tickWork(registry);
        assertEquals(request.workTicks() - 1, machine.getAssemblyTicksRemaining());

        NBTTagCompound saved = new NBTTagCompound();
        machine.writeToNBT(saved);
        FormedAssembler restored = new FormedAssembler();
        restored.readFromNBT(saved);
        restored.attachServerWorld();
        for (int i = 1; i < request.workTicks(); i++) restored.tickWork(registry);
        assertEquals(0, restored.storedEU());
        assertEquals(request, restored.completedOutput());
        restored.tickWork(registry);
        assertEquals(request, restored.completedOutput());
        assertEquals(Admission.BUSY, restored.tryAccept(TEAM, request()));
        saved = new NBTTagCompound();
        restored.writeToNBT(saved);
        FormedAssembler holding = new FormedAssembler();
        holding.readFromNBT(saved);
        assertEquals(request, holding.completedOutput());
        assertFalse(holding.acknowledgeOutput(UUID.randomUUID()));
        assertTrue(holding.acknowledgeOutput(request.workId()));
        assertTrue(holding.acknowledgeOutput(request.workId()));
        assertNull(holding.completedOutput());
    }

    @Test
    void powerLossPausesPaidRepairWithoutProgressOrMaterialCharges() throws Exception {
        FormedAssembler machine = supplied();
        machine.attachServerWorld();
        WorkRequest request = request();
        machine.tryAccept(TEAM, request);
        machine.receiveAssignedModule(request.workId(), request.vehicleId(), request.moduleId());
        machine.tickWork(registry);
        assertEquals(request.workTicks(), machine.getAssemblyTicksRemaining());
        machine.injectEnergyUnits(ForgeDirection.NORTH, request.euPerTick(), 1);
        machine.tickWork(registry);
        machine.tickWork(registry);
        assertEquals(request.workTicks() - 1, machine.getAssemblyTicksRemaining());
        assertEquals(0, machine.storedEU());
        assertEquals(2, machine.getStackInSlot(0).stackSize);
        assertNull(machine.completedOutput());
    }

    /** Product contract: paid native work waits unchanged while its concrete vehicle is incomplete. */
    @Test
    void incompleteVehiclePausesItsPaidWorkWithoutChargingEnergy() {
        var machine = supplied();
        var request = request();
        assertTrue(
            machine.tryAccept(TEAM, request)
                .accepted());
        machine.receiveAssignedModule(request.workId(), request.vehicleId(), request.moduleId());
        machine.injectEnergyUnits(ForgeDirection.NORTH, request.euPerTick(), request.workTicks());
        var rocket = registry.find(request.vehicleId());
        var holder = new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId());
        assertTrue(
            rocket.acceptProduced(
                Rocket.Module.produced(
                    rocket.id(),
                    rocket.buildSlots()
                        .getFirst(),
                    40,
                    holder)));
        assertEquals(
            1,
            rocket.dropModules(holder)
                .size());
        assertEquals(Rocket.Status.INCOMPLETE, rocket.status());
        machine.tickWork(registry);
        assertEquals(request.workTicks(), machine.getAssemblyTicksRemaining());
        assertEquals(request.euPerTick() * request.workTicks(), machine.storedEU());
        assertNull(machine.completedOutput());
    }

    private static FormedAssembler supplied() {
        FormedAssembler machine = new FormedAssembler();
        machine.setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 4));
        machine.fill(ForgeDirection.UNKNOWN, new FluidStack(FluidRegistry.WATER, 200), true);
        return machine;
    }

    private WorkRequest request() {
        var design = new RocketBlueprint();
        design.addPart(new RocketPartInstance(RocketPartDef.structural(1, "work_test", 1, 1, 1, null), 0, 0, 0, false));
        var rocket = Rocket
            .construct(TEAM, CelestialObjectKey.registered(CelestialObjectId.OVERWORLD), UUID.randomUUID(), design);
        registry.register(rocket);
        return new WorkRequest(
            UUID.randomUUID(),
            rocket.id(),
            rocket.buildSlots()
                .getFirst()
                .moduleId(),
            1,
            Operation.REPAIR,
            Map.of(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 2L),
            Map.of(new FluidKey(FluidRegistry.WATER, null), 100L),
            10,
            3);
    }

    private static final class FormedAssembler extends TileEntityModuleAssembler {

        private FormedAssembler() {
            super(LIMITS);
            machineIdentity().claim(TEAM);
            structureValid = true;
        }

        /** Harness exception: only World.isRemote is needed by this server tick contract. */
        private void attachServerWorld() throws Exception {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            worldObj = (World) ((Unsafe) field.get(null)).allocateInstance(WorldServer.class);
            structureValid = true;
        }

        @Override
        protected boolean checkStructure() {
            return true;
        }

        @Override
        public void markDirty() {}
    }
}
