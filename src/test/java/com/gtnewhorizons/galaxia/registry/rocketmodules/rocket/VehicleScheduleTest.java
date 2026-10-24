package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint.Kind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner.RoutePriority;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule.Context;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule.Deploy;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule.Direction;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule.Exact;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule.Group;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule.Leg;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule.Mode;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule.Place;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule.Refuel;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule.Repair;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule.RouteSettings;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule.Transfer;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule.Visit;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class VehicleScheduleTest {

    @BeforeAll
    static void initializeRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void editingOneVisitLeavesAcceptedScheduleAndOtherVisitsIndependent() {
        var first = visit();
        var second = visit();
        var input = new ArrayList<>(List.of(first, second));
        var accepted = new VehicleSchedule(input, Mode.RUN_ONCE);
        input.clear();

        var repair = new Repair(UUID.randomUUID(), 40);
        var draft = accepted.withVisit(new Visit(first.id(), first.place(), List.of(repair), true));

        assertEquals(List.of(first, second), accepted.visits());
        assertEquals(
            List.of(repair),
            draft.visits()
                .get(0)
                .actions());
        assertEquals(
            second,
            draft.visits()
                .get(1));
        assertFalse(
            accepted.visits()
                .get(0)
                .blockDockActions());
        assertTrue(
            draft.visits()
                .get(0)
                .blockDockActions());
        assertThrows(
            UnsupportedOperationException.class,
            () -> draft.visits()
                .clear());
        assertThrows(
            UnsupportedOperationException.class,
            () -> draft.visits()
                .get(0)
                .actions()
                .clear());
    }

    @Test
    void reorderRetainsOnlyUnchangedAdjacentRouteSettings() {
        var a = visit();
        var b = visit();
        var c = visit();
        var d = visit();
        var ab = new Leg(a.id(), b.id());
        var bc = new Leg(b.id(), c.id());
        var cd = new Leg(c.id(), d.id());
        var settings = new RouteSettings(1500, RoutePriority.PRIORITIZE_DV);
        var accepted = new VehicleSchedule(List.of(a, b, c, d), Mode.RUN_ONCE).withRouteSettings(ab, settings)
            .withRouteSettings(bc, settings)
            .withRouteSettings(cd, settings);

        var draft = accepted.reordered(List.of(a.id(), b.id(), d.id(), c.id()));

        assertEquals(List.of(a, b, d, c), draft.visits());
        assertEquals(
            settings,
            draft.routes()
                .get(ab));
        assertEquals(
            3,
            draft.routes()
                .size());
        assertFalse(
            draft.routes()
                .containsKey(bc));
        assertFalse(
            draft.routes()
                .get(new Leg(b.id(), d.id()))
                .allowance()
                .isPresent());
        assertFalse(
            draft.routes()
                .get(new Leg(d.id(), c.id()))
                .allowance()
                .isPresent());
        assertEquals(
            settings,
            accepted.routes()
                .get(bc));
    }

    @Test
    void loopAddsClosingRouteAndRunOnceRemovesIt() {
        var first = visit();
        var last = visit();
        var closing = new Leg(last.id(), first.id());
        var once = new VehicleSchedule(List.of(first, last), Mode.RUN_ONCE);
        var loop = once.withMode(Mode.LOOP);

        assertEquals(
            1,
            once.routes()
                .size());
        assertEquals(
            2,
            loop.routes()
                .size());
        assertFalse(
            loop.routes()
                .get(closing)
                .allowance()
                .isPresent());
        var configured = loop.withRouteSettings(closing, new RouteSettings(0, RoutePriority.PRIORITIZE_TOF));
        assertEquals(
            0,
            configured.routes()
                .get(closing)
                .allowance()
                .orElseThrow());
        assertFalse(
            configured.withMode(Mode.RUN_ONCE)
                .routes()
                .containsKey(closing));
        assertTrue(
            new VehicleSchedule(List.of(first), Mode.LOOP).routes()
                .isEmpty());
    }

    @Test
    void invalidRouteAllowanceAndNonPermutationReorderCannotBeAccepted() {
        var a = visit();
        var b = visit();
        var schedule = new VehicleSchedule(List.of(a, b), Mode.RUN_ONCE);

        for (double invalid : new double[] { -1, Double.NaN, Double.POSITIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class, () -> new RouteSettings(invalid, RoutePriority.PRIORITIZE_DV));
        }
        assertThrows(IllegalArgumentException.class, () -> schedule.reordered(List.of(a.id(), a.id())));
        assertThrows(IllegalArgumentException.class, () -> schedule.reordered(List.of(a.id())));
        assertThrows(
            IllegalArgumentException.class,
            () -> schedule
                .withRouteSettings(new Leg(b.id(), a.id()), new RouteSettings(10, RoutePriority.PRIORITIZE_DV)));
    }

    @Test
    void repairAdmitsEachModuleAtOrBelowThresholdWithoutChangingTargetDurability() {
        var action = new Repair(UUID.randomUUID(), 40);
        var candidates = List.of(0.0, 20.0, 40.0, 40.1, 100.0);

        assertEquals(
            List.of(0.0, 20.0, 40.0),
            candidates.stream()
                .filter(action::qualifies)
                .toList());
        assertThrows(IllegalArgumentException.class, () -> action.qualifies(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> action.qualifies(-1));
        assertThrows(IllegalArgumentException.class, () -> new Repair(UUID.randomUUID(), 101));
    }

    @Test
    void repairWithExactReturnSiloPreservesItsMandatoryHandlingTargetAfterReload() {
        UUID silo = UUID.randomUUID();
        var action = new Repair(UUID.randomUUID(), 75, silo);
        var stop = new Visit(UUID.randomUUID(), visit().place(), List.of(action));
        var schedule = new VehicleSchedule(List.of(stop), Mode.RUN_ONCE);
        var restored = VehicleSchedule.deserializeNBT(schedule.serializeNBT());
        assertEquals(
            action,
            restored.visits()
                .getFirst()
                .actions()
                .getFirst());
        assertEquals(
            silo,
            ((Repair) restored.visits()
                .getFirst()
                .actions()
                .getFirst()).returnSiloId());
        assertFalse(action.qualifies(95));
    }

    @Test
    void repairSavedWithoutReturnTargetRetainsItsConditionalBehavior() {
        var action = new Repair(UUID.randomUUID(), 75);
        var stop = new Visit(UUID.randomUUID(), visit().place(), List.of(action));
        var saved = new VehicleSchedule(List.of(stop), Mode.RUN_ONCE).serializeNBT();
        var encoded = saved.getTagList("visits", NBT.TAG_COMPOUND)
            .getCompoundTagAt(0)
            .getTagList("actions", NBT.TAG_COMPOUND)
            .getCompoundTagAt(0);
        assertFalse(encoded.hasKey("returnSilo"));
        var restored = VehicleSchedule.deserializeNBT(saved);
        assertNull(
            ((Repair) restored.visits()
                .getFirst()
                .actions()
                .getFirst()).returnSiloId());
        assertEquals(
            action,
            restored.visits()
                .getFirst()
                .actions()
                .getFirst());
    }

    @Test
    void persistencePreservesVisitActionResourceAndRouteIdentity() {
        var body = CelestialObjectKey.registered(CelestialObjectId.MARS);
        var stack = new ItemStack(Items.stick);
        var payloadState = new NBTTagCompound();
        payloadState.setString("payload", "satellite-alpha");
        stack.setTagCompound(payloadState);
        var cargo = ItemStackWrapper.of(stack);
        var fluid = new FluidKey(FluidRegistry.WATER, null);
        var first = new Visit(
            UUID.randomUUID(),
            new Place(body, Context.SURFACE, new Exact(UUID.randomUUID(), Kind.SILO)),
            List.of(
                new Transfer(UUID.randomUUID(), cargo, 4, Direction.LOAD),
                new Refuel(UUID.randomUUID(), Map.of(fluid, 800L))),
            true);
        var second = new Visit(
            UUID.randomUUID(),
            new Place(body, Context.ORBIT, null),
            List.of(new Deploy(UUID.randomUUID(), cargo, 1)));
        var last = new Visit(
            UUID.randomUUID(),
            new Place(body, Context.SURFACE, new Group("repair", Kind.LANDING_PAD, UUID.randomUUID())),
            List.of(new Repair(UUID.randomUUID(), 40)));
        var leg = new Leg(first.id(), second.id());
        var accepted = new VehicleSchedule(List.of(first, second, last), Mode.LOOP)
            .withRouteSettings(leg, new RouteSettings(1200, RoutePriority.PRIORITIZE_TOF))
            .withSafeDurability(false);

        var restored = VehicleSchedule.deserializeNBT(accepted.serializeNBT());

        assertEquals(accepted, restored);
        assertFalse(
            restored.routes()
                .get(new Leg(last.id(), first.id()))
                .allowance()
                .isPresent());
        assertEquals(
            cargo,
            ((Deploy) restored.visits()
                .get(1)
                .actions()
                .get(0)).cargo());
    }

    @Test
    void repeatedPlacesHaveIndependentActionIdentityAndPropellantTargets() {
        var fluid = new FluidKey(FluidRegistry.WATER, null);
        var targets = new HashMap<FluidKey, Long>();
        targets.put(fluid, 800L);
        var refuel = new Refuel(UUID.randomUUID(), targets);
        var first = visit();
        var second = new Visit(UUID.randomUUID(), first.place(), List.of(new Repair(UUID.randomUUID(), 30)));
        var schedule = new VehicleSchedule(List.of(first, second), Mode.RUN_ONCE);

        targets.put(fluid, 1L);
        var draft = schedule.withVisit(new Visit(first.id(), first.place(), List.of(refuel)));

        assertEquals(Map.of(fluid, 800L), refuel.targets());
        assertEquals(
            List.of(),
            schedule.visits()
                .get(0)
                .actions());
        assertEquals(
            second.actions(),
            draft.visits()
                .get(1)
                .actions());
        assertThrows(
            UnsupportedOperationException.class,
            () -> refuel.targets()
                .clear());
        assertThrows(
            IllegalArgumentException.class,
            () -> schedule.withVisit(new Visit(first.id(), first.place(), second.actions())));
    }

    @Test
    void removingVisitRegeneratesRouteAndMalformedStoredAdjacencyIsRejected() {
        var a = visit();
        var b = visit();
        var c = visit();
        var schedule = new VehicleSchedule(List.of(a, b, c), Mode.RUN_ONCE);
        var edited = schedule.withoutVisit(b.id());

        assertEquals(List.of(a, c), edited.visits());
        assertEquals(
            1,
            edited.routes()
                .size());
        assertFalse(
            edited.routes()
                .get(new Leg(a.id(), c.id()))
                .allowance()
                .isPresent());
        var malformed = schedule.serializeNBT();
        malformed.getTagList("routes", NBT.TAG_COMPOUND)
            .getCompoundTagAt(0)
            .setString(
                "from",
                UUID.randomUUID()
                    .toString());
        assertThrows(IllegalArgumentException.class, () -> VehicleSchedule.deserializeNBT(malformed));
    }

    private static Visit visit() {
        return new Visit(
            UUID.randomUUID(),
            new Place(CelestialObjectKey.registered(CelestialObjectId.MARS), Context.SURFACE, null),
            List.of());
    }
}
