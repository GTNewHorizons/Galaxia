package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class RocketTest {

    @BeforeAll
    static void bootstrap() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    // Integration contract: replacing or retiring an entity never replaces the concrete vehicle or accepts a stale
    // projection.
    @Test
    void reloadedVehicleAcceptsOnlyItsCurrentEntityProjection() {
        var design = design();
        var definitions = new RocketPartRegistry();
        definitions.register(
            design.getParts()
                .getFirst()
                .def(),
            RocketPartCategory.STRUCTURAL);
        var rocket = Rocket.construct(
            UUID.randomUUID(),
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            UUID.randomUUID(),
            design);
        assertEquals(null, rocket.claimProjection(UUID.randomUUID()));
        var slot = rocket.buildSlots()
            .getFirst();
        assertTrue(
            rocket.acceptProduced(
                Rocket.Module.produced(
                    rocket.id(),
                    slot,
                    75,
                    new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId()))));
        assertTrue(rocket.assemble(rocket.assemblySiloId()));
        UUID entityId = UUID.randomUUID();
        var original = rocket.claimProjection(entityId);
        assertEquals(original, rocket.claimProjection(entityId));
        assertTrue(rocket.ownsProjection(entityId, original.generation()));

        var restored = Rocket.deserializeNBT(rocket.serializeNBT(), definitions);
        assertEquals(original, restored.projection());
        var replacement = restored.claimProjection(UUID.randomUUID());
        assertTrue(replacement.generation() > original.generation());
        assertFalse(restored.ownsProjection(entityId, original.generation()));
        assertFalse(restored.retireProjection(entityId, original.generation()));
        assertTrue(restored.retireProjection(replacement.entityId(), replacement.generation()));
        assertEquals(null, restored.projection());
        assertEquals(rocket.id(), restored.id());
        assertEquals(
            75,
            restored.module(slot.moduleId())
                .durabilityPercent());
        assertEquals(Rocket.Status.PHYSICAL, restored.status());
    }

    @Test
    void suppliedConstructionKeepsFrozenBillAndInstallsTheSameModuleExactlyOnce() {
        var price = new RocketPartDef.WorkBill(
            Map.of(com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 2L),
            10,
            3);
        var definition = RocketPartDef.structural(2, "frame", 1, 1, 1, null)
            .withManufactureBill(price);
        var definitions = new RocketPartRegistry();
        definitions.register(definition, RocketPartCategory.STRUCTURAL);
        var design = new RocketBlueprint();
        assertTrue(design.addPart(new RocketPartInstance(definition, 0, 0, 0, false)));
        var rocket = Rocket.construct(
            UUID.randomUUID(),
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            UUID.randomUUID(),
            design);
        var slot = rocket.buildSlots()
            .getFirst();
        UUID assemblerId = UUID.randomUUID();
        var source = new Rocket.Holder(Rocket.HolderKind.ASSEMBLER, assemblerId);
        var machine = new Rocket.TerminalTarget(UUID.randomUUID(), assemblerId, 0, 1, 2, 3, source);
        assertTrue(rocket.reserveWork(new Rocket.ModuleWork(slot.requestId(), slot.moduleId(), machine, price)));
        var saved = rocket.serializeNBT();
        definitions
            .replace(
                definition.withManufactureBill(
                    new RocketPartDef.WorkBill(
                        Map.of(
                            com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper
                                .of(new ItemStack(Items.iron_ingot)),
                            7L),
                        20,
                        9)));
        var restored = Rocket.deserializeNBT(saved, definitions);
        assertEquals(
            price,
            restored.workFor(slot.moduleId())
                .bill());
        assertFalse(restored.assemble(restored.assemblySiloId()));
        var restoredSlot = restored.buildSlots()
            .getFirst();
        assertTrue(restored.acceptProduced(Rocket.Module.produced(restored.id(), restoredSlot, 100, source)));
        var destination = new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, restored.assemblySiloId());
        var delivery = restored.beginTransfer(slot.moduleId(), slot.requestId(), source, destination);
        assertEquals(Rocket.TransferResult.ACCEPTED, restored.confirmTransfer(delivery, destination));
        assertTrue(restored.assemble(restored.assemblySiloId()));
        assertFalse(restored.assemble(restored.assemblySiloId()));
        var assembled = Rocket.deserializeNBT(restored.serializeNBT(), definitions);
        assertEquals(Rocket.Status.PHYSICAL, assembled.status());
        assertEquals(
            1,
            assembled.modules()
                .size());
        assertEquals(
            new Rocket.Holder(Rocket.HolderKind.INSTALLED, assembled.id()),
            assembled.module(slot.moduleId())
                .holder());
        assertEquals(
            1,
            assembled.builtBlueprint()
                .getParts()
                .size());
    }

    @Test
    void gantryReloadKeepsReservedTripAndPartialHopOnTheSameModule() {
        var design = design();
        var definitions = new RocketPartRegistry();
        definitions.register(
            design.getParts()
                .getFirst()
                .def(),
            RocketPartCategory.STRUCTURAL);
        var rocket = Rocket.construct(
            UUID.randomUUID(),
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            UUID.randomUUID(),
            design);
        var slot = rocket.buildSlots()
            .getFirst();
        var source = new Rocket.Holder(Rocket.HolderKind.ASSEMBLER, UUID.randomUUID());
        var destination = new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId());
        assertTrue(rocket.acceptProduced(Rocket.Module.produced(rocket.id(), slot, 50, source)));
        UUID work = UUID.randomUUID();
        UUID firstNode = UUID.randomUUID();
        UUID lastNode = UUID.randomUUID();
        var target = new Rocket.TerminalTarget(lastNode, rocket.assemblySiloId(), 0, 10, 20, 30, destination);
        assertTrue(rocket.beginGantryTransport(slot.moduleId(), work, source, target, List.of(firstNode, lastNode)));
        var firstHolder = new Rocket.Holder(Rocket.HolderKind.GANTRY, firstNode);
        var entry = rocket.beginTransfer(slot.moduleId(), work, source, firstHolder);
        assertEquals(Rocket.TransferResult.ACCEPTED, rocket.confirmTransfer(entry, firstHolder));
        var lastHolder = new Rocket.Holder(Rocket.HolderKind.GANTRY, lastNode);
        var hop = rocket.beginTransfer(slot.moduleId(), work, firstHolder, lastHolder);
        assertFalse(rocket.advanceGantryProgress(slot.moduleId(), 0.4));

        var restored = Rocket.deserializeNBT(rocket.serializeNBT(), definitions);
        var inTransit = restored.module(slot.moduleId());
        assertEquals(firstHolder, inTransit.holder());
        assertEquals(hop, inTransit.pendingTransfer());
        assertEquals(
            List.of(firstNode, lastNode),
            inTransit.gantryTransport()
                .reservedRoute());
        assertEquals(
            0.4,
            inTransit.gantryTransport()
                .progress());
        assertTrue(restored.advanceGantryProgress(slot.moduleId(), 0.6));
        assertEquals(Rocket.TransferResult.ACCEPTED, restored.confirmTransfer(hop, lastHolder));
        assertEquals(
            0,
            restored.module(slot.moduleId())
                .gantryTransport()
                .progress());
        assertFalse(restored.finishGantryTransport(slot.moduleId(), work));
        var delivery = restored.beginTransfer(slot.moduleId(), work, lastHolder, destination);
        assertEquals(Rocket.TransferResult.ACCEPTED, restored.confirmTransfer(delivery, destination));
        assertTrue(restored.finishGantryTransport(slot.moduleId(), work));
        assertTrue(restored.finishGantryTransport(slot.moduleId(), work));
        assertEquals(
            null,
            restored.module(slot.moduleId())
                .gantryTransport());
    }

    @Test
    void cargoAndPropellantStayInsideTheirConcreteModuleDuringRepairAndReload() {
        var fuel = new FluidKey(FluidRegistry.WATER, null);
        var definition = RocketPartDef.structural(1, "sealed_storage", 1, 1, 1, null)
            .withStorage(new RocketPartDef.Storage(1, 1000, Map.of(fuel, 1000L)));
        var definitions = new RocketPartRegistry();
        definitions.register(definition, RocketPartCategory.STRUCTURAL);
        var design = new RocketBlueprint();
        assertTrue(design.addPart(new RocketPartInstance(definition, 0, 0, 0, false)));
        var rocket = Rocket.construct(
            UUID.randomUUID(),
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            UUID.randomUUID(),
            design);
        var slot = rocket.buildSlots()
            .getFirst();
        var installed = new Rocket.Holder(Rocket.HolderKind.INSTALLED, rocket.id());
        var module = Rocket.Module.produced(rocket.id(), slot, 40, installed);
        assertTrue(rocket.acceptProduced(module));
        var cargo = new ItemStack(Items.iron_ingot, 17);
        cargo.setTagCompound(new NBTTagCompound());
        cargo.getTagCompound()
            .setString("payload", "retained");
        assertEquals(null, module.insertCargo(cargo, false));
        assertEquals(600, module.fillPropellant(fuel.toStack(600), false));
        assertEquals(700, module.fillFreight(new FluidStack(FluidRegistry.LAVA, 700), false));

        var assembler = new Rocket.Holder(Rocket.HolderKind.ASSEMBLER, UUID.randomUUID());
        var transfer = rocket.beginTransfer(module.id(), UUID.randomUUID(), installed, assembler);
        assertEquals(Rocket.TransferResult.ACCEPTED, rocket.confirmTransfer(transfer, assembler));
        var restored = Rocket.deserializeNBT(rocket.serializeNBT(), definitions)
            .module(module.id());

        assertEquals(
            17,
            restored.cargoContents()
                .getFirst().stackSize);
        assertEquals(
            "retained",
            restored.cargoContents()
                .getFirst()
                .getTagCompound()
                .getString("payload"));
        assertEquals(
            600,
            restored.propellantContents()
                .get(fuel));
        assertEquals(700, restored.freightContents().amount);
        assertEquals(
            FluidRegistry.LAVA,
            restored.freightContents()
                .getFluid());
        assertEquals(1, restored.insertCargo(new ItemStack(Items.iron_ingot), false).stackSize);
        assertEquals(0, restored.fillPropellant(fuel.toStack(1), false));
        assertEquals(0, restored.fillFreight(new FluidStack(FluidRegistry.LAVA, 1), false));
        var copy = restored.cargoContents()
            .getFirst();
        copy.stackSize = 1;
        assertEquals(
            17,
            restored.cargoContents()
                .getFirst().stackSize);
    }

    @Test
    void scheduleSaveRequiresCurrentRevisionAndVehicleTeamAndSurvivesReload() {
        RocketBlueprint design = design();
        var definitions = new RocketPartRegistry();
        definitions.register(
            design.getParts()
                .getFirst()
                .def(),
            RocketPartCategory.STRUCTURAL);
        UUID team = UUID.randomUUID();
        var rocket = Rocket
            .construct(team, CelestialObjectKey.registered(CelestialObjectId.OVERWORLD), UUID.randomUUID(), design);
        var visit = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            new VehicleSchedule.Place(rocket.bodyKey(), VehicleSchedule.Context.ORBIT, null),
            List.of(new VehicleSchedule.Repair(UUID.randomUUID(), 75)));
        var edited = rocket.schedule()
            .withVisit(visit);
        long revision = rocket.revision();

        assertEquals(Rocket.CommandResult.FORBIDDEN, rocket.saveSchedule(UUID.randomUUID(), revision, edited));
        assertTrue(
            rocket.schedule()
                .visits()
                .isEmpty());
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(team, revision, edited));
        assertEquals(
            Rocket.CommandResult.STALE,
            rocket.saveSchedule(team, revision, edited.withMode(VehicleSchedule.Mode.LOOP)));
        assertEquals(edited, rocket.schedule());

        var restored = Rocket.deserializeNBT(rocket.serializeNBT(), definitions);
        assertEquals(edited, restored.schedule());
        assertEquals(rocket.revision(), restored.revision());
    }

    @Test
    void identicalVehiclesCannotAcceptEachOthersReservedModules() {
        RocketBlueprint design = design();
        UUID teamId = UUID.randomUUID();
        UUID siloId = UUID.randomUUID();
        var body = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        Rocket first = Rocket.construct(teamId, body, siloId, design);
        Rocket second = Rocket.construct(teamId, body, UUID.randomUUID(), design);
        Rocket.BuildSlot slot = first.buildSlots()
            .getFirst();
        Rocket.Module module = Rocket.Module
            .produced(first.id(), slot, 75.0, new Rocket.Holder(Rocket.HolderKind.ASSEMBLER, UUID.randomUUID()));

        assertFalse(second.acceptProduced(module));
        assertTrue(
            second.modules()
                .isEmpty());
        assertTrue(first.acceptProduced(module));
        assertTrue(first.acceptProduced(module));
        assertEquals(
            1,
            first.modules()
                .size());
        assertEquals(
            first.id(),
            first.module(slot.moduleId())
                .vehicleId());
        assertEquals(
            75.0,
            first.module(slot.moduleId())
                .durabilityPercent());
    }

    private static RocketBlueprint design() {
        RocketBlueprint design = new RocketBlueprint();
        assertTrue(
            design
                .addPart(new RocketPartInstance(RocketPartDef.structural(1, "frame", 1, 1, 1, null), 0, 0, 0, false)));
        return design;
    }

    @Test
    void transferKeepsSourceCustodyUntilTheExactReceiverAccepts() {
        Rocket rocket = Rocket.construct(
            UUID.randomUUID(),
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            UUID.randomUUID(),
            design());
        var slot = rocket.buildSlots()
            .getFirst();
        var source = new Rocket.Holder(Rocket.HolderKind.ASSEMBLER, UUID.randomUUID());
        var destination = new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId());
        assertTrue(rocket.acceptProduced(Rocket.Module.produced(rocket.id(), slot, 75, source)));
        UUID workId = UUID.randomUUID();

        var transfer = rocket.beginTransfer(slot.moduleId(), workId, source, destination);
        assertEquals(
            source,
            rocket.module(slot.moduleId())
                .holder());
        assertEquals(transfer, rocket.beginTransfer(slot.moduleId(), workId, source, destination));
        assertEquals(
            Rocket.TransferResult.REJECTED,
            rocket.confirmTransfer(transfer, new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, UUID.randomUUID())));
        assertEquals(
            source,
            rocket.module(slot.moduleId())
                .holder());
        assertEquals(Rocket.TransferResult.ACCEPTED, rocket.confirmTransfer(transfer, destination));
        assertEquals(Rocket.TransferResult.ALREADY_ACCEPTED, rocket.confirmTransfer(transfer, destination));
        assertEquals(
            destination,
            rocket.module(slot.moduleId())
                .holder());
        assertEquals(
            1,
            rocket.modules()
                .size());
    }

    @Test
    void reloadKeepsVehicleIdentityAndPendingHandoffWithoutRepeatingAnAcceptedTransfer() {
        RocketBlueprint design = design();
        RocketPartRegistry definitions = new RocketPartRegistry();
        definitions.register(
            design.getParts()
                .getFirst()
                .def(),
            RocketPartCategory.STRUCTURAL);
        Rocket rocket = Rocket.construct(
            UUID.randomUUID(),
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            UUID.randomUUID(),
            design);
        var slot = rocket.buildSlots()
            .getFirst();
        var source = new Rocket.Holder(Rocket.HolderKind.ASSEMBLER, UUID.randomUUID());
        var destination = new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId());
        assertTrue(rocket.acceptProduced(Rocket.Module.produced(rocket.id(), slot, 75, source)));
        var transfer = rocket.beginTransfer(slot.moduleId(), UUID.randomUUID(), source, destination);

        Rocket restored = Rocket.deserializeNBT(rocket.serializeNBT(), definitions);
        assertEquals(rocket.id(), restored.id());
        assertEquals(rocket.teamId(), restored.teamId());
        assertEquals(rocket.buildSlots(), restored.buildSlots());
        assertEquals(
            source,
            restored.module(slot.moduleId())
                .holder());
        assertEquals(
            transfer,
            restored.module(slot.moduleId())
                .pendingTransfer());
        assertEquals(Rocket.TransferResult.ACCEPTED, restored.confirmTransfer(transfer, destination));

        Rocket afterDelivery = Rocket.deserializeNBT(restored.serializeNBT(), definitions);
        assertEquals(Rocket.TransferResult.ALREADY_ACCEPTED, afterDelivery.confirmTransfer(transfer, destination));
        assertEquals(
            destination,
            afterDelivery.module(slot.moduleId())
                .holder());
        assertEquals(
            75,
            afterDelivery.module(slot.moduleId())
                .durabilityPercent());
    }
}
