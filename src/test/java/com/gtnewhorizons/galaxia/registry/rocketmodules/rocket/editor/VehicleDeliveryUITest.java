package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint.Kind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class VehicleDeliveryUITest {

    private static final CelestialObjectKey BODY = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);

    @BeforeAll
    static void bootstrap() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    @Test
    void switchingViewsPreservesAdvancedAndSimpleDraftsWithoutSavingEither() {
        var home = silo();
        var target = silo();
        var accepted = schedule(home);
        var session = new VehicleScheduleUI.Session();
        session.receive(view(UUID.randomUUID(), 4, accepted, home, null, false), catalog(home, target), null, false);
        var advanced = accepted.withSafeDurability(false);
        session.edit(advanced);

        session.showSimple();
        session.simpleDestination(choice(target));
        var iron = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        session.simpleRequest(iron, 500);
        var simple = session.simplePlan();
        session.showAdvanced();

        assertEquals(advanced, session.draft());
        assertEquals(
            accepted,
            session.view()
                .schedule());
        assertEquals(simple, session.simplePlan());
        assertFalse(session.simpleMode());
        assertTrue(session.dirty());
        session.showSimple();
        assertEquals(
            500L,
            session.simplePlan()
                .requested()
                .get(iron));
    }

    @Test
    void simpleNavigationRetainsRequestsAndOriginalRevision() {
        var home = silo();
        var target = silo();
        var id = UUID.randomUUID();
        var accepted = schedule(home);
        var catalog = catalog(home, target);
        var session = new VehicleScheduleUI.Session();
        session.receive(view(id, 4, accepted, home, null, false), catalog, null, false);
        session.showSimple();
        session.simpleDestination(choice(target));
        session.simpleRequest(ItemStackWrapper.of(new ItemStack(Items.gold_ingot)), 180);
        var saved = session.simplePlan();

        var resumed = new VehicleScheduleUI.Session();
        resumed.restoreNavigation(session.navigationState());
        resumed.receive(view(id, 5, accepted, home, null, false), catalog, null, false);

        assertTrue(resumed.simpleMode());
        assertEquals(saved, resumed.simplePlan());
        assertEquals(4, resumed.simpleBaseRevision());
        assertEquals(accepted, resumed.draft());
    }

    @Test
    void simpleRequestsUseExplicitCommandAndManagedAdvancedRouteStaysReadOnly() {
        var home = silo();
        var target = silo();
        var id = UUID.randomUUID();
        var accepted = schedule(home);
        var catalog = catalog(home, target);
        var session = new VehicleScheduleUI.Session();
        var submitted = new AtomicReference<Rocket.DeliveryPlan>();
        session.deliveryCommands(draft -> submitted.set(draft.simplePlan()), ignored -> {}, ignored -> {});
        session.receive(view(id, 4, accepted, home, null, false), catalog, null, false);
        session.showSimple();
        session.simpleDestination(choice(target));
        session.simpleRequest(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 100);
        session.requestDeliverySave();

        assertTrue(session.pending());
        assertEquals(
            100L,
            submitted.get()
                .requested()
                .values()
                .iterator()
                .next());
        var delivery = new Rocket.DeliveryView(
            submitted.get(),
            Map.of(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 0L),
            true);
        session.receive(view(id, 5, accepted, home, delivery, false), catalog, null, true);
        session.showAdvanced();
        session.edit(accepted.withMode(VehicleSchedule.Mode.LOOP));

        assertEquals(accepted, session.draft());
        assertFalse(
            session.editable(
                accepted.visits()
                    .getFirst()
                    .id()));
        assertFalse(session.pending());
    }

    @Test
    void stoppedGeneratorCannotBeDetachedBeforeItsCurrentTripFinishes() {
        var home = silo();
        var target = silo();
        var iron = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        var delivery = new Rocket.DeliveryView(
            new Rocket.DeliveryPlan(home, target, Map.of(iron, 100L)),
            Map.of(iron, 0L),
            false);
        var detached = new AtomicBoolean();
        var session = new VehicleScheduleUI.Session();
        session.deliveryCommands(ignored -> {}, ignored -> {}, ignored -> detached.set(true));
        session.receive(
            view(UUID.randomUUID(), 4, schedule(home), home, delivery, true),
            catalog(home, target),
            null,
            false);

        session.requestDeliveryClear();

        assertFalse(session.simpleEditable());
        assertFalse(detached.get());
        assertFalse(session.pending());
    }

    private static VehicleSchedule.Place silo() {
        return new VehicleSchedule.Place(
            BODY,
            VehicleSchedule.Context.SURFACE,
            new VehicleSchedule.Exact(UUID.randomUUID(), Kind.SILO));
    }

    private static VehicleScheduleUI.EndpointChoice choice(VehicleSchedule.Place place) {
        var exact = (VehicleSchedule.Exact) place.target();
        return new VehicleScheduleUI.EndpointChoice(
            BODY,
            place.context(),
            exact.endpointId(),
            exact.kind(),
            "Silo",
            null);
    }

    private static VehicleSchedule schedule(VehicleSchedule.Place home) {
        return new VehicleSchedule(
            List.of(new VehicleSchedule.Visit(UUID.randomUUID(), home, List.of())),
            VehicleSchedule.Mode.RUN_ONCE);
    }

    private static VehicleScheduleUI.Catalog catalog(VehicleSchedule.Place home, VehicleSchedule.Place target) {
        return new VehicleScheduleUI.Catalog(
            List.of(),
            List.of(choice(home), choice(target)),
            List.of(),
            List.of(),
            0,
            null,
            null);
    }

    private static Rocket.ScheduleView view(UUID id, long revision, VehicleSchedule schedule,
        VehicleSchedule.Place home, Rocket.DeliveryView delivery, boolean running) {
        return new Rocket.ScheduleView(
            id,
            revision,
            schedule,
            Rocket.Status.PHYSICAL,
            home,
            null,
            Set.of(),
            null,
            null,
            Map.of(),
            Map.of(),
            List.of(),
            0,
            null,
            null,
            null,
            delivery,
            running);
    }
}
