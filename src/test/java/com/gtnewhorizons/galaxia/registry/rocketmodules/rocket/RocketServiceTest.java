package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class RocketServiceTest {

    @BeforeAll
    static void bootstrap() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    // Product contract: admission freezes the per-module <= threshold decision for this landing occurrence.
    @Test
    void serviceDisassemblesSameModulesAndQualifiesOnlyThoseAtOrBelowThreshold() {
        Rocket rocket = assembled(List.of(74.0, 75.0, 76.0));
        UUID pad = UUID.randomUUID();
        UUID receivingSilo = UUID.randomUUID();
        UUID occurrence = UUID.randomUUID();
        var action = new VehicleSchedule.Repair(UUID.randomUUID(), 75);
        var moduleIds = rocket.modules()
            .stream()
            .map(Rocket.Module::id)
            .toList();

        assertEquals(Rocket.ServiceResult.ACCEPTED, rocket.beginService(occurrence, action, pad, receivingSilo));
        assertEquals(Rocket.Status.DISASSEMBLED, rocket.status());
        assertEquals(
            moduleIds,
            rocket.modules()
                .stream()
                .map(Rocket.Module::id)
                .toList());
        assertEquals(
            2,
            rocket.service()
                .repairs()
                .size());
        assertTrue(
            rocket.service()
                .repairs()
                .containsKey(moduleIds.get(0)));
        assertTrue(
            rocket.service()
                .repairs()
                .containsKey(moduleIds.get(1)));
        assertFalse(
            rocket.service()
                .repairs()
                .containsKey(moduleIds.get(2)));
        assertEquals(
            3,
            rocket.service()
                .returns()
                .size());
        assertEquals(
            receivingSilo,
            rocket.service()
                .returnSiloId());
        for (var module : rocket.modules()) {
            assertEquals(new Rocket.Holder(Rocket.HolderKind.PAD_BUFFER, pad), module.holder());
        }
        var accepted = rocket.service();
        assertEquals(Rocket.ServiceResult.ALREADY_ACTIVE, rocket.beginService(occurrence, action, pad, receivingSilo));
        assertEquals(accepted, rocket.service());
        assertEquals(Rocket.ServiceResult.CONFLICT, rocket.beginService(UUID.randomUUID(), action, pad, receivingSilo));
    }

    // Integration contract: world reload cannot lose the accepted work identities or requalify modules.
    @Test
    void reloadRetainsAcceptedServiceAndExactModuleCustody() {
        Rocket rocket = assembled(List.of(45.0, 90.0));
        UUID pad = UUID.randomUUID();
        UUID receivingSilo = UUID.randomUUID();
        UUID occurrence = UUID.randomUUID();
        var action = new VehicleSchedule.Repair(UUID.randomUUID(), 75);
        assertEquals(Rocket.ServiceResult.ACCEPTED, rocket.beginService(occurrence, action, pad, receivingSilo));
        var definitions = new RocketPartRegistry();
        definitions.register(
            rocket.buildSlots()
                .getFirst()
                .part()
                .def(),
            RocketPartCategory.STRUCTURAL);

        var restored = Rocket.deserializeNBT(rocket.serializeNBT(), definitions);

        assertEquals(rocket.service(), restored.service());
        assertEquals(rocket.id(), restored.id());
        assertEquals(Rocket.Status.DISASSEMBLED, restored.status());
        assertEquals(
            Rocket.ServiceResult.ALREADY_ACTIVE,
            restored.beginService(occurrence, new VehicleSchedule.Repair(action.id(), 100), pad, receivingSilo));
        assertEquals(
            1,
            restored.service()
                .repairs()
                .size());
        for (var module : restored.modules()) {
            assertEquals(
                rocket.module(module.id())
                    .durabilityPercent(),
                module.durabilityPercent());
            assertEquals(new Rocket.Holder(Rocket.HolderKind.PAD_BUFFER, pad), module.holder());
        }
    }

    // Product contract: only the assigned completed repair restores a module, and all exact parts must return.
    @Test
    void repairAndReassemblyRequireAssignedWorkAndTheSelectedSilo() {
        Rocket rocket = assembled(List.of(40.0, 90.0));
        UUID pad = UUID.randomUUID();
        UUID receivingSilo = UUID.randomUUID();
        UUID occurrence = UUID.randomUUID();
        var action = new VehicleSchedule.Repair(UUID.randomUUID(), 75);
        var originalSchedule = rocket.schedule();
        assertEquals(Rocket.ServiceResult.ACCEPTED, rocket.beginService(occurrence, action, pad, receivingSilo));
        var worn = rocket.modules()
            .getFirst();
        var skipped = rocket.modules()
            .getLast();
        UUID assemblerId = UUID.randomUUID();
        UUID repairWork = rocket.service()
            .repairs()
            .get(worn.id());
        var assembler = new Rocket.Holder(Rocket.HolderKind.ASSEMBLER, assemblerId);
        var price = new RocketPartDef.WorkBill(Map.of(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 1L), 10, 3);
        var machine = new Rocket.TerminalTarget(UUID.randomUUID(), assemblerId, 0, 1, 2, 3, assembler);
        assertTrue(rocket.reserveWork(new Rocket.ModuleWork(repairWork, worn.id(), machine, price)));
        assertFalse(rocket.completeRepair(worn.id(), repairWork, assemblerId));
        var delivery = rocket.beginTransfer(worn.id(), repairWork, worn.holder(), assembler);
        assertFalse(rocket.completeRepair(worn.id(), repairWork, assemblerId));
        assertEquals(Rocket.TransferResult.ACCEPTED, rocket.confirmTransfer(delivery, assembler));
        assertFalse(rocket.completeRepair(worn.id(), UUID.randomUUID(), assemblerId));
        assertTrue(rocket.completeRepair(worn.id(), repairWork, assemblerId));
        assertEquals(100, worn.durabilityPercent());
        assertEquals(90, skipped.durabilityPercent());

        var definitions = new RocketPartRegistry();
        definitions.register(
            worn.part()
                .def(),
            RocketPartCategory.STRUCTURAL);
        rocket = Rocket.deserializeNBT(rocket.serializeNBT(), definitions);
        worn = rocket.module(worn.id());
        skipped = rocket.module(skipped.id());
        assertTrue(rocket.completeRepair(worn.id(), repairWork, assemblerId));
        assertFalse(rocket.assemble(receivingSilo));
        var silo = new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, receivingSilo);
        var repairedReturn = rocket.beginTransfer(
            worn.id(),
            rocket.service()
                .returns()
                .get(worn.id()),
            worn.holder(),
            silo);
        assertEquals(Rocket.TransferResult.ACCEPTED, rocket.confirmTransfer(repairedReturn, silo));
        assertFalse(rocket.assemble(receivingSilo));
        var skippedReturn = rocket.beginTransfer(
            skipped.id(),
            rocket.service()
                .returns()
                .get(skipped.id()),
            skipped.holder(),
            silo);
        assertEquals(Rocket.TransferResult.ACCEPTED, rocket.confirmTransfer(skippedReturn, silo));
        assertFalse(rocket.assemble(UUID.randomUUID()));
        assertTrue(rocket.assemble(receivingSilo));
        assertEquals(receivingSilo, rocket.assemblySiloId());
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(originalSchedule, rocket.schedule());
        assertEquals(Rocket.ServiceResult.COMPLETED, rocket.beginService(occurrence, action, pad, receivingSilo));
        assertFalse(rocket.assemble(receivingSilo));
        assertEquals(Rocket.ServiceResult.SKIPPED, rocket.beginService(UUID.randomUUID(), action, pad, receivingSilo));
    }

    // Product contract: future visits remain editable while the accepted physical service visit stays fixed.
    @Test
    void activeServiceVisitCannotBeDeletedOrMovedBySavingAnotherDraft() {
        Rocket rocket = assembled(List.of(40.0));
        UUID pad = UUID.randomUUID();
        UUID returnSilo = UUID.randomUUID();
        var action = new VehicleSchedule.Repair(UUID.randomUUID(), 75);
        var serviceVisit = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            new VehicleSchedule.Place(
                rocket.bodyKey(),
                VehicleSchedule.Context.SURFACE,
                new VehicleSchedule.Exact(pad, CelestialAsset.Endpoint.Kind.LANDING_PAD)),
            List.of(action));
        var future = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            new VehicleSchedule.Place(rocket.bodyKey(), VehicleSchedule.Context.ORBIT, null),
            List.of());
        var schedule = new VehicleSchedule(List.of(serviceVisit, future), VehicleSchedule.Mode.RUN_ONCE);
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(rocket.teamId(), rocket.revision(), schedule));
        assertEquals(Rocket.ServiceResult.ACCEPTED, rocket.beginService(UUID.randomUUID(), action, pad, returnSilo));
        assertEquals(
            serviceVisit.id(),
            rocket.scheduleView()
                .activeVisitId());
        assertTrue(
            rocket.scheduleView()
                .lockedVisitIds()
                .contains(serviceVisit.id()));
        var editedFuture = new VehicleSchedule.Visit(future.id(), future.place(), List.of(), true);
        assertEquals(
            Rocket.CommandResult.APPLIED,
            rocket.saveSchedule(rocket.teamId(), rocket.revision(), schedule.withVisit(editedFuture)));
        var accepted = rocket.schedule();
        assertEquals(
            Rocket.CommandResult.BLOCKED,
            rocket.saveSchedule(rocket.teamId(), rocket.revision(), accepted.withoutVisit(serviceVisit.id())));
        assertEquals(
            Rocket.CommandResult.BLOCKED,
            rocket.saveSchedule(
                rocket.teamId(),
                rocket.revision(),
                accepted.reordered(List.of(future.id(), serviceVisit.id()))));
        assertEquals(accepted, rocket.schedule());
    }

    private static Rocket assembled(List<Double> durabilities) {
        var design = new RocketBlueprint();
        var definition = RocketPartDef.structural(1, "service_frame", 1, 1, 1, null);
        for (int i = 0; i < durabilities.size(); i++) {
            assertTrue(design.addPart(new RocketPartInstance(definition, i, 0, 0, false)));
        }
        var rocket = Rocket.construct(
            UUID.randomUUID(),
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            UUID.randomUUID(),
            design);
        var buffer = new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId());
        for (int i = 0; i < rocket.buildSlots()
            .size(); i++) {
            assertTrue(
                rocket.acceptProduced(
                    Rocket.Module.produced(
                        rocket.id(),
                        rocket.buildSlots()
                            .get(i),
                        durabilities.get(i),
                        buffer)));
        }
        assertTrue(rocket.assemble(rocket.assemblySiloId()));
        return rocket;
    }
}
