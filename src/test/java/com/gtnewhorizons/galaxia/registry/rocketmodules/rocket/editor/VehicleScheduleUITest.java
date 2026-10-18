package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;

/** Integration contracts for the same draft operations used by production widgets. */
final class VehicleScheduleUITest {

    /** Bug regression: navigation never silently rebases a dirty draft onto a newer server revision. */
    @Test
    void bodyMapRoundTripKeepsDraftIdentityAndOriginalSaveRevision() {
        var source = visit();
        var destination = visit();
        var accepted = new VehicleSchedule(List.of(source, destination), VehicleSchedule.Mode.RUN_ONCE);
        var vehicle = UUID.randomUUID();
        var session = new VehicleScheduleUI.Session();
        var catalog = bodyCatalog();
        session.receive(riskView(vehicle, 4, accepted, source.id(), null), catalog, null, false);
        var edited = new VehicleSchedule.Visit(
            destination.id(),
            destination.place(),
            List.of(new VehicleSchedule.Repair(UUID.randomUUID(), 35)));
        session.replaceVisit(edited);
        session.selectVisit(destination.id());
        var before = session.draft();
        var restored = new VehicleScheduleUI.Session();

        restored.restoreNavigation(session.navigationState());
        restored.receive(riskView(vehicle, 5, accepted, source.id(), null), catalog, null, false);

        assertEquals(before, restored.draft());
        assertEquals(4, restored.baseRevision());
        assertTrue(restored.dirty());
        assertTrue(restored.selectDestinationBody(CelestialObjectKey.registered(CelestialObjectId.MOON)));
        var selected = restored.draft()
            .visits()
            .getLast();
        assertEquals(destination.id(), selected.id());
        assertEquals(edited.actions(), selected.actions());
        assertEquals(
            CelestialObjectKey.registered(CelestialObjectId.MOON),
            selected.place()
                .body());
        assertEquals(
            source,
            restored.draft()
                .visits()
                .getFirst());
        assertEquals(4, restored.baseRevision());
        restored.receive(riskView(vehicle, 6, accepted, destination.id(), null), catalog, null, false);
        assertFalse(restored.selectDestinationBody(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD)));
        assertEquals(
            selected,
            restored.draft()
                .visits()
                .getLast());
    }

    /** Integration contract: malformed client navigation state is rejected at its codec boundary. */
    @Test
    void navigationRejectsMissingAndMalformedState() {
        var session = new VehicleScheduleUI.Session();
        assertThrows(IllegalArgumentException.class, () -> session.restoreNavigation(null));
        var source = visit();
        session.receive(
            riskView(
                UUID.randomUUID(),
                4,
                new VehicleSchedule(List.of(source), VehicleSchedule.Mode.RUN_ONCE),
                source.id(),
                null),
            VehicleScheduleUI.Catalog.empty(),
            null,
            false);
        var state = session.navigationState();
        state.removeTag("baseRevision");
        assertThrows(IllegalArgumentException.class, () -> new VehicleScheduleUI.Session().restoreNavigation(state));
    }

    /** Product contract: cancelling navigation keeps manual coordinates and route input unchanged. */
    @Test
    void bodyMapCancelKeepsManualAndAutomatedDrafts() {
        var source = visit();
        var accepted = new VehicleSchedule(List.of(source), VehicleSchedule.Mode.RUN_ONCE);
        var vehicle = UUID.randomUUID();
        var session = new VehicleScheduleUI.Session();
        session.receive(riskView(vehicle, 4, accepted, source.id(), null), bodyCatalog(), null, false);
        session.addVisit();
        session.openManual();
        session.manualDestination(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD), "123", "-456");
        var route = new VehicleSchedule.RouteSettings(
            12500,
            com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF);
        session.manualRoute(route);
        var restored = new VehicleScheduleUI.Session();

        restored.restoreNavigation(session.navigationState());
        restored.receive(riskView(vehicle, 4, accepted, source.id(), null), bodyCatalog(), null, false);

        assertEquals(session.draft(), restored.draft());
        assertTrue(restored.manualMode());
        assertEquals("123", restored.manualX());
        assertEquals("-456", restored.manualZ());
        assertEquals(route, restored.manualRoute());
        assertFalse(restored.selectDestinationBody(CelestialObjectKey.registered(CelestialObjectId.MARS)));
        assertEquals(session.manualBody(), restored.manualBody());
        assertTrue(restored.selectDestinationBody(CelestialObjectKey.registered(CelestialObjectId.MOON)));
        assertEquals("123", restored.manualX());
        assertEquals("-456", restored.manualZ());
        assertEquals(route, restored.manualRoute());
        assertEquals(session.draft(), restored.draft());
    }

    private static VehicleScheduleUI.Catalog bodyCatalog() {
        var bodies = com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry.numericalDefinitions(200000);
        return new VehicleScheduleUI.Catalog(
            List.of(
                bodies.get(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD)),
                bodies.get(CelestialObjectKey.registered(CelestialObjectId.MOON))),
            List.of(),
            List.of(),
            List.of(),
            0,
            null,
            null);
    }

    @Test
    void manualLandingAcceptanceReopensAndCancelsWithoutChangingTheAutomatedDraft() {
        var source = visit();
        var automated = new VehicleSchedule(List.of(source), VehicleSchedule.Mode.RUN_ONCE);
        var body = CelestialObjectKey.registered(CelestialObjectId.MOON);
        var destination = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            new VehicleSchedule.Place(
                body,
                VehicleSchedule.Context.SURFACE,
                null,
                new VehicleSchedule.SurfaceSite(7, 120, 70, -40, new Rocket.Footprint(3, 8, 3))),
            List.of());
        var leg = new VehicleSchedule.Leg(source.id(), destination.id());
        var mission = new VehicleSchedule(List.of(source, destination), VehicleSchedule.Mode.RUN_ONCE)
            .withRouteSettings(
                leg,
                new VehicleSchedule.RouteSettings(
                    500,
                    com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV));
        var vehicle = UUID.randomUUID();
        var session = new VehicleScheduleUI.Session();
        var acceptedSite = new AtomicReference<VehicleSchedule.Place>();
        var cancelled = new AtomicReference<UUID>();
        session.manualCommands(
            state -> {},
            state -> acceptedSite.set(state.landingSite()),
            state -> cancelled.set(
                state.view()
                    .vehicleId()));
        session.receive(
            riskView(vehicle, 4, automated, source.id(), null),
            VehicleScheduleUI.Catalog.empty(),
            null,
            false);
        session.openManual();
        session.manualDestination(body, "", "");
        session.manualRoute(
            mission.routes()
                .get(leg));
        session.requestLandingSearch();
        long query = session.landingQuery();
        session.receiveLandingSearch(vehicle, 4, query, destination.place(), null);
        assertTrue(session.canAcceptLanding());
        session.manualDestination(body, "1", "");
        assertFalse(session.canAcceptLanding());
        session.receiveLandingSearch(vehicle, 4, query, destination.place(), null);
        assertFalse(session.canAcceptLanding());
        session.requestLandingSearch();
        session.receiveLandingSearch(vehicle, 4, session.landingQuery(), destination.place(), null);

        session.requestLandingAcceptance();

        assertEquals(destination.place(), acceptedSite.get());
        assertEquals(automated, session.draft());
        assertTrue(session.pending());
        session.receive(manualView(vehicle, 5, automated, mission), VehicleScheduleUI.Catalog.empty(), null, true);
        assertFalse(session.pending());
        assertTrue(session.manualMode());
        assertEquals("120", session.manualX());
        assertEquals("-40", session.manualZ());
        assertEquals(automated, session.draft());
        var reopened = new VehicleScheduleUI.Session();
        reopened.receive(manualView(vehicle, 5, automated, mission), VehicleScheduleUI.Catalog.empty(), null, false);
        assertTrue(reopened.manualMode());
        assertEquals(destination.place(), reopened.landingSite());
        assertEquals(
            mission.routes()
                .get(leg),
            reopened.manualRoute());

        session.requestLandingCancellation();

        assertEquals(vehicle, cancelled.get());
        assertTrue(session.pending());
        session
            .receive(riskView(vehicle, 6, automated, source.id(), null), VehicleScheduleUI.Catalog.empty(), null, true);
        assertFalse(session.manualMode());
        assertFalse(session.pending());
        assertEquals(automated, session.draft());
    }

    private static Rocket.ScheduleView manualView(UUID vehicle, long revision, VehicleSchedule automated,
        VehicleSchedule mission) {
        return new Rocket.ScheduleView(
            vehicle,
            revision,
            automated,
            Rocket.Status.PHYSICAL,
            mission.visits()
                .getFirst()
                .place(),
            mission.visits()
                .getFirst()
                .id(),
            Set.of(
                mission.visits()
                    .getFirst()
                    .id()),
            null,
            null,
            Map.of(),
            Map.of(),
            List.of(),
            0,
            null,
            null,
            mission);
    }

    @Test
    void manualSearchRepliesBelongToTheirQueryAndClosingDiscardsPendingSearch() {
        var source = visit();
        var schedule = new VehicleSchedule(List.of(source), VehicleSchedule.Mode.RUN_ONCE);
        var vehicle = UUID.randomUUID();
        var session = new VehicleScheduleUI.Session();
        session
            .receive(riskView(vehicle, 4, schedule, source.id(), null), VehicleScheduleUI.Catalog.empty(), null, false);
        var searches = new java.util.concurrent.atomic.AtomicInteger();
        session.manualCommands(state -> searches.incrementAndGet(), state -> {}, state -> {});
        session.openManual();
        session.manualDestination(
            source.place()
                .body(),
            "12",
            "");
        session.requestLandingSearch();
        long first = session.landingQuery();
        assertTrue(session.searchingLanding());
        session.manualDestination(
            source.place()
                .body(),
            "13",
            "");
        assertFalse(session.searchingLanding());
        session.requestLandingSearch();
        long second = session.landingQuery();
        session.receiveLandingSearch(vehicle, 4, first, null, "old query");
        session.receiveLandingSearch(UUID.randomUUID(), 4, second, null, "other vehicle");
        session.receiveLandingSearch(vehicle, 3, second, null, "old revision");
        assertTrue(session.searchingLanding());
        session.receiveLandingSearch(vehicle, 4, second, null, "no site");
        assertFalse(session.searchingLanding());
        assertEquals(schedule, session.draft());
        assertFalse(session.dirty());
        session.requestLandingSearch();
        long closing = session.landingQuery();

        session.cancel();

        assertFalse(session.searchingLanding());
        assertTrue(session.landingQuery() > closing);
        session.receiveLandingSearch(vehicle, 4, closing, null, "late reply");
        assertFalse(session.searchingLanding());
        assertEquals(3, searches.get());
    }

    @Test
    void riskReviewKeepsUnsavedFutureEditsAndOnlySaveReplacesTheDraft() {
        var source = visit();
        var destination = visit();
        var future = visit();
        var accepted = new VehicleSchedule(List.of(source, destination, future), VehicleSchedule.Mode.RUN_ONCE);
        var vehicle = UUID.randomUUID();
        var warning = new Rocket.LaunchWarning(
            new VehicleSchedule.Leg(source.id(), destination.id()),
            source.place(),
            destination.place(),
            100,
            25,
            true);
        var session = new VehicleScheduleUI.Session();
        var catalog = VehicleScheduleUI.Catalog.empty();
        session.receive(riskView(vehicle, 4, accepted, source.id(), null), catalog, null, false);
        var editedFuture = new VehicleSchedule.Visit(
            future.id(),
            future.place(),
            List.of(new VehicleSchedule.Repair(UUID.randomUUID(), 35)));
        session.replaceVisit(editedFuture);
        var draft = session.draft();
        var decision = new AtomicReference<Boolean>();
        var reviewedRevision = new AtomicReference<Long>();
        var saved = new AtomicReference<VehicleSchedule>();
        session.commands(state -> saved.set(state.draft()), null, (state, proceed) -> {
            decision.set(proceed);
            reviewedRevision.set(
                state.view()
                    .revision());
        });
        session.receive(riskView(vehicle, 5, accepted, source.id(), warning), catalog, null, false);
        assertFalse(session.editable(future.id()));

        session.requestRiskReview(false);

        assertEquals(Boolean.FALSE, decision.get());
        assertEquals(5L, reviewedRevision.get());
        assertTrue(session.pending());
        session.receive(riskView(vehicle, 6, accepted, source.id(), null), catalog, null, true);
        assertEquals(draft, session.draft());
        assertEquals(6, session.baseRevision());
        assertFalse(session.pending());
        assertTrue(session.editable(future.id()));
        assertFalse(session.editable(source.id()));

        session.receive(riskView(vehicle, 7, accepted, source.id(), warning), catalog, null, false);
        session.requestRiskReview(true);
        assertEquals(Boolean.TRUE, decision.get());
        assertEquals(7L, reviewedRevision.get());
        session.receive(riskView(vehicle, 8, accepted, source.id(), null), catalog, null, true);
        assertEquals(draft, session.draft());
        assertEquals(8, session.baseRevision());

        session.requestSave();
        assertEquals(draft, saved.get());
        session.receive(riskView(vehicle, 9, saved.get(), source.id(), null), catalog, null, true);
        assertFalse(session.dirty());
        assertFalse(session.pending());
    }

    private static Rocket.ScheduleView riskView(UUID vehicle, long revision, VehicleSchedule schedule, UUID source,
        Rocket.LaunchWarning warning) {
        return new Rocket.ScheduleView(
            vehicle,
            revision,
            schedule,
            Rocket.Status.PHYSICAL,
            schedule.visits()
                .getFirst()
                .place(),
            source,
            Set.of(source),
            null,
            null,
            Map.of(),
            Map.of(),
            List.of(),
            0,
            null,
            warning,
            null);
    }

    @Test
    void firstDraftVisitKeepsTheCurrentOrbitalEndpoint() {
        var place = new VehicleSchedule.Place(
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            VehicleSchedule.Context.ORBIT,
            new VehicleSchedule.Exact(
                UUID.randomUUID(),
                com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint.Kind.SILO));
        var session = new VehicleScheduleUI.Session();
        session.receive(
            new Rocket.ScheduleView(
                UUID.randomUUID(),
                4,
                new VehicleSchedule(List.of(), VehicleSchedule.Mode.RUN_ONCE),
                Rocket.Status.PHYSICAL,
                place,
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
                null),
            VehicleScheduleUI.Catalog.empty(),
            null,
            false);

        session.addVisit();

        assertEquals(
            place,
            session.draft()
                .visits()
                .getFirst()
                .place());
    }

    @Test
    void reorderAndCancelKeepAcceptedScheduleAndVisitIdentities() {
        var first = visit();
        var second = visit();
        var accepted = new VehicleSchedule(List.of(first, second), VehicleSchedule.Mode.RUN_ONCE);
        var session = new VehicleScheduleUI.Session();
        var view = new Rocket.ScheduleView(
            UUID.randomUUID(),
            4,
            accepted,
            Rocket.Status.PHYSICAL,
            first.place(),
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
            null);
        session.receive(view, VehicleScheduleUI.Catalog.empty(), null, false);

        session.moveVisit(second.id(), -1);

        assertEquals(
            List.of(second.id(), first.id()),
            session.draft()
                .visits()
                .stream()
                .map(VehicleSchedule.Visit::id)
                .toList());
        assertEquals(List.of(first, second), accepted.visits());
        assertTrue(session.dirty());

        session.cancel();

        assertEquals(accepted, session.draft());
        assertFalse(session.dirty());
    }

    @Test
    void staleSaveKeepsDraftUntilCancelAndAcknowledgedSaveUsesNewRevision() {
        var visit = visit();
        var accepted = new VehicleSchedule(List.of(visit), VehicleSchedule.Mode.RUN_ONCE);
        var id = UUID.randomUUID();
        var session = new VehicleScheduleUI.Session();
        var catalog = VehicleScheduleUI.Catalog.empty();
        session.receive(
            new Rocket.ScheduleView(
                id,
                4,
                accepted,
                Rocket.Status.PHYSICAL,
                visit.place(),
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
                null),
            catalog,
            null,
            false);
        var submitted = new AtomicReference<VehicleSchedule>();
        session.commands(draft -> submitted.set(draft.draft()), null, null);
        session.edit(accepted.withMode(VehicleSchedule.Mode.LOOP));
        session.requestSave();
        assertTrue(session.pending());
        assertEquals(
            VehicleSchedule.Mode.LOOP,
            submitted.get()
                .mode());

        session.receive(
            new Rocket.ScheduleView(
                id,
                5,
                accepted,
                Rocket.Status.PHYSICAL,
                visit.place(),
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
                null),
            catalog,
            "galaxia.rocket.schedule.stale",
            true);
        assertFalse(session.pending());
        assertTrue(session.dirty());
        assertEquals(4, session.baseRevision());

        session.cancel();
        assertEquals(5, session.baseRevision());
        assertEquals(accepted, session.draft());
        session.edit(accepted.withSafeDurability(false));
        session.requestSave();
        session.receive(
            new Rocket.ScheduleView(
                id,
                6,
                submitted.get(),
                Rocket.Status.PHYSICAL,
                visit.place(),
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
                null),
            catalog,
            null,
            true);
        assertFalse(session.dirty());
        assertFalse(session.pending());
        assertEquals(6, session.baseRevision());
    }

    @Test
    void activeVisitStaysReadOnlyWhileFutureVisitCanBeEdited() {
        var active = visit();
        var future = visit();
        var accepted = new VehicleSchedule(List.of(active, future), VehicleSchedule.Mode.RUN_ONCE);
        var session = new VehicleScheduleUI.Session();
        session.receive(
            new Rocket.ScheduleView(
                UUID.randomUUID(),
                4,
                accepted,
                Rocket.Status.PHYSICAL,
                active.place(),
                active.id(),
                Set.of(active.id()),
                null,
                null,
                Map.of(),
                Map.of(),
                List.of(),
                0,
                null,
                null,
                null),
            VehicleScheduleUI.Catalog.empty(),
            null,
            false);
        session.moveVisit(future.id(), -1);
        session.replaceVisit(
            new VehicleSchedule.Visit(
                active.id(),
                active.place(),
                List.of(new VehicleSchedule.Repair(UUID.randomUUID(), 35))));
        assertEquals(accepted, session.draft());

        var repair = new VehicleSchedule.Repair(UUID.randomUUID(), 35);
        session.replaceVisit(new VehicleSchedule.Visit(future.id(), future.place(), List.of(repair)));
        assertEquals(
            active,
            session.draft()
                .visits()
                .getFirst());
        assertEquals(
            List.of(repair),
            session.draft()
                .visits()
                .getLast()
                .actions());
        assertTrue(session.dirty());
    }

    private static VehicleSchedule.Visit visit() {
        return new VehicleSchedule.Visit(
            UUID.randomUUID(),
            new VehicleSchedule.Place(
                CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
                VehicleSchedule.Context.SURFACE,
                null),
            List.of());
    }
}
