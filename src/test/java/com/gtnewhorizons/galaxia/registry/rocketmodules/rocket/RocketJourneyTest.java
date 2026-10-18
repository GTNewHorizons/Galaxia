package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.config.ConfigRocket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner.RoutePriority;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import sun.misc.Unsafe;

final class RocketJourneyTest {

    private static FluidKey FUEL;
    private static FluidKey OXIDIZER;
    private static final List<CelestialAsset.ID> PREPARED_RETURNS = new java.util.ArrayList<>();

    @BeforeAll
    static void bootstrap() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        GalaxiaTestBootstrap.ensureFacilityModules();
        FacilityModuleRegistry.registerLandingBay(Map.of(new ItemStack(Items.iron_ingot), 1L));
        FUEL = new FluidKey(FluidRegistry.WATER, null);
        OXIDIZER = new FluidKey(FluidRegistry.LAVA, null);
    }

    @AfterEach
    void removePreparedReturnAssets() {
        PREPARED_RETURNS.forEach(CelestialAssetStore.SERVER::destroyAssetInternal);
        PREPARED_RETURNS.clear();
    }

    // Product contract: automatic travel requires a prepared surface destination.
    @Test
    void automatedSchedulesRejectUnresolvedSurfaceDestinations() {
        var rocket = assembled(definitions());
        var before = rocket.schedule();
        var unresolved = new VehicleSchedule(
            List.of(
                new VehicleSchedule.Visit(UUID.randomUUID(), rocket.location(), List.of()),
                new VehicleSchedule.Visit(
                    UUID.randomUUID(),
                    new VehicleSchedule.Place(rocket.bodyKey(), VehicleSchedule.Context.SURFACE, null),
                    List.of())),
            VehicleSchedule.Mode.RUN_ONCE);
        assertEquals(Rocket.CommandResult.BLOCKED, rocket.saveSchedule(rocket.teamId(), rocket.revision(), unresolved));
        assertEquals(before, rocket.schedule());
    }

    // Integration contract: a displayed site is claimed once across teams, survives reload and is freed on cancel.
    @Test
    void acceptedManualSiteSurvivesReloadWithoutChangingTheAutomatedSchedule() {
        var definitions = definitions();
        var first = assembled(definitions);
        var second = assembled(definitions);
        var saved = schedule(first, false);
        assertEquals(Rocket.CommandResult.APPLIED, first.saveSchedule(first.teamId(), first.revision(), saved));
        var registry = new Rocket.Registry();
        registry.register(first);
        registry.register(second);
        var site = manualSite(first, 100, 100);
        var settings = new VehicleSchedule.RouteSettings(30000, RoutePriority.PRIORITIZE_DV);
        assertTrue(registry.landingSiteAvailable(first.id(), site));
        assertEquals(
            Rocket.CommandResult.FORBIDDEN,
            registry.acceptManualLanding(first.id(), second.teamId(), first.revision(), site, settings));
        assertEquals(
            Rocket.CommandResult.STALE,
            registry.acceptManualLanding(first.id(), first.teamId(), first.revision() - 1, site, settings));
        assertEquals(
            Rocket.CommandResult.APPLIED,
            registry.acceptManualLanding(first.id(), first.teamId(), first.revision(), site, settings));
        assertEquals(saved, first.schedule());
        assertNotNull(
            first.scheduleView()
                .manualMission());
        assertFalse(registry.landingSiteAvailable(second.id(), site));
        assertEquals(
            Rocket.CommandResult.BLOCKED,
            registry.acceptManualLanding(second.id(), second.teamId(), second.revision(), site, settings));
        var id = first.id();
        registry.restoreNBT(registry.serializeNBT(), definitions);
        first = registry.find(id);
        second = registry.find(second.id());
        assertEquals(saved, first.schedule());
        assertEquals(
            site,
            first.scheduleView()
                .manualMission()
                .visits()
                .getLast()
                .place());
        assertFalse(registry.landingSiteAvailable(second.id(), site));
        assertEquals(Rocket.CommandResult.APPLIED, first.cancelManualLanding(first.teamId(), first.revision()));
        assertTrue(registry.landingSiteAvailable(second.id(), site));
        assertEquals(
            Rocket.CommandResult.APPLIED,
            registry.acceptManualLanding(second.id(), second.teamId(), second.revision(), site, settings));
    }

    // Product contract: the common flight executor preserves the accepted physical site and changes no saved itinerary.
    @Test
    void manualFlightKeepsItsSiteFuelAndIdentityThroughReloadAndArrival() {
        var definitions = definitions();
        var rocket = assembled(definitions);
        var registry = new Rocket.Registry();
        registry.register(rocket);
        var site = manualSite(rocket, 100, 100);
        var id = rocket.id();
        var modules = rocket.modules()
            .stream()
            .map(Rocket.Module::id)
            .toList();
        var saved = rocket.schedule();
        assertEquals(
            Rocket.CommandResult.APPLIED,
            registry.acceptManualLanding(
                id,
                rocket.teamId(),
                rocket.revision(),
                site,
                new VehicleSchedule.RouteSettings(30000, RoutePriority.PRIORITIZE_DV)));
        rocket.tick(registry, 0);
        assertEquals(Rocket.Status.IN_FLIGHT, rocket.status());
        var accepted = rocket.scheduleView()
            .manualMission();
        saved = schedule(rocket, false);
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(rocket.teamId(), rocket.revision(), saved));
        assertEquals(
            accepted,
            rocket.scheduleView()
                .manualMission());
        assertEquals(Rocket.CommandResult.BLOCKED, rocket.cancelManualLanding(rocket.teamId(), rocket.revision()));
        rocket.tick(registry, 1);
        var fuel = rocket.propellantAmounts();
        registry.restoreNBT(registry.serializeNBT(), definitions);
        rocket = registry.find(id);
        rocket.tick(registry, 2);
        assertEquals(fuel, rocket.propellantAmounts());
        for (int tick = 3; tick < 2000 && rocket.isRunning(); tick++) rocket.tick(registry, tick);
        assertFalse(rocket.isRunning());
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(site, rocket.location());
        assertEquals(
            site.surfaceSite()
                .position(),
            rocket.physicalPosition());
        assertEquals(
            modules,
            rocket.modules()
                .stream()
                .map(Rocket.Module::id)
                .toList());
        assertEquals(saved, rocket.schedule());
        var other = assembled(definitions);
        registry.register(other);
        assertFalse(registry.landingSiteAvailable(other.id(), site));
        assertFalse(registry.landingSiteAvailable(other.id(), manualSite(other, 0, 0)));
    }

    /** Bug regression: a landing representation starts airborne and resumes the same descent after reload. */
    @Test
    void landingProjectionStartsAboveItsDestinationAndResumesUntilTouchdown() throws Exception {
        double originalMinimum = ConfigRocket.flight.minimumSurfaceManeuverSeconds;
        ConfigRocket.flight.minimumSurfaceManeuverSeconds = 4;
        try {
            var definitions = definitions();
            var rocket = assembled(definitions);
            var registry = new Rocket.Registry();
            registry.register(rocket);
            var site = manualSite(rocket, 100, 100);
            var anchor = site.surfaceSite()
                .position();
            var id = rocket.id();
            assertEquals(
                Rocket.CommandResult.APPLIED,
                registry.acceptManualLanding(
                    id,
                    rocket.teamId(),
                    rocket.revision(),
                    site,
                    new VehicleSchedule.RouteSettings(30000, RoutePriority.PRIORITIZE_DV)));
            for (int tick = 0; tick < 2000; tick++) {
                var phase = rocket.physicalManeuver();
                if (phase != null && phase.kind() == OrbitalMechanics.ManeuverKind.LANDING) break;
                rocket.tick(registry, tick);
            }
            assertNotNull(rocket.physicalManeuver());
            assertEquals(
                OrbitalMechanics.ManeuverKind.LANDING,
                rocket.physicalManeuver()
                    .kind());
            var world = projectionWorld();
            var first = new EntityRocket(world);
            first.setPositionAndRotation(anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), 0);
            assertTrue(first.bind(rocket));
            assertTrue(
                first.posY > anchor.y() + rocket.footprint()
                    .height(),
                "Landing must spawn above the destination");
            assertEquals(first.posY, first.prevPosY, "The first rendered frame must already be airborne");
            assertEquals(anchor, rocket.physicalPosition());
            long minimumTicks = (long) Math.ceil(ConfigRocket.flight.minimumSurfaceManeuverSeconds * 20);
            var phase = rocket.physicalManeuver();
            double plannedTicks = (phase.endEpochOsu() - phase.startEpochOsu()) / OrbitalTransferPlanner.OSU_PER_TICK;
            assertTrue(plannedTicks >= minimumTicks - 1e-6);
            for (int tick = 0; tick < minimumTicks / 2; tick++) rocket.tick(registry, tick);
            assertEquals(Rocket.Status.IN_FLIGHT, rocket.status());
            assertNotEquals(site, rocket.location());
            assertTrue(rocket.physicalManeuverProgress() > 0 && rocket.physicalManeuverProgress() < 1);
            assertEquals(
                rocket.physicalManeuverProgress(),
                rocket.transitView()
                    .phaseProgress(),
                1e-6);
            double progress = rocket.physicalManeuverProgress();
            var fuel = rocket.propellantAmounts();
            registry.restoreNBT(registry.serializeNBT(), definitions);
            rocket = registry.find(id);
            assertEquals(progress, rocket.physicalManeuverProgress());
            assertEquals(fuel, rocket.propellantAmounts());
            var resumed = new EntityRocket(world);
            resumed.setPositionAndRotation(anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), 0);
            assertTrue(resumed.bind(rocket));
            assertTrue(resumed.posY > anchor.y() && resumed.posY < first.posY, "Reload must resume partway down");
            assertEquals(resumed.posY, resumed.prevPosY);
            assertEquals(anchor, rocket.physicalPosition());
            for (int tick = 0; tick < 2000 && rocket.isRunning(); tick++) rocket.tick(registry, tick);
            assertFalse(rocket.isRunning());
            assertEquals(Rocket.Status.PHYSICAL, rocket.status());
            assertEquals(site, rocket.location());
            var landed = new EntityRocket(world);
            landed.setPositionAndRotation(anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), 0);
            assertTrue(landed.bind(rocket));
            assertEquals(anchor.y(), landed.posY);
        } finally {
            ConfigRocket.flight.minimumSurfaceManeuverSeconds = originalMinimum;
        }
    }

    /** Harness exception: only the external World identity is needed to bind an unspawned native entity. */
    private static World projectionWorld() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        World world = (World) ((Unsafe) unsafeField.get(null)).allocateInstance(WorldServer.class);
        Field provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(world, new WorldProviderSurface());
        return world;
    }

    private static VehicleSchedule.Place manualSite(Rocket rocket, int x, int z) {
        return new VehicleSchedule.Place(
            rocket.bodyKey(),
            VehicleSchedule.Context.SURFACE,
            null,
            new VehicleSchedule.SurfaceSite(0, x, 64, z, rocket.footprint()));
    }

    // Product contract: reload retains the chosen flight and never charges an active maneuver twice.
    @Test
    void chargedManeuverSurvivesReloadAndReturnsTheSameVehicle() {
        var definitions = definitions();
        var rocket = assembled(definitions);
        var originalId = rocket.id();
        var modules = rocket.modules()
            .stream()
            .map(Rocket.Module::id)
            .toList();
        var schedule = schedule(rocket, true);
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(rocket.teamId(), rocket.revision(), schedule));
        assertTrue(rocket.board(UUID.randomUUID()));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.start(rocket.teamId(), rocket.revision()));
        var registry = new Rocket.Registry();
        registry.register(rocket);
        rocket.tick(registry, 0);
        assertEquals(Rocket.Status.IN_FLIGHT, rocket.status());
        rocket.tick(registry, 1);
        var charged = rocket.propellantAmounts();
        assertTrue(charged.get(FUEL) < 1000);
        assertEquals((1000 - charged.get(FUEL)) * 2, 2000 - charged.get(OXIDIZER));
        registry.restoreNBT(registry.serializeNBT(), definitions);
        rocket = registry.find(originalId);
        rocket.tick(registry, 2);
        assertEquals(charged, rocket.propellantAmounts());
        for (int tick = 3; tick < 2000 && rocket.isRunning(); tick++) rocket.tick(registry, tick);
        assertFalse(rocket.isRunning());
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(
            VehicleSchedule.Context.SURFACE,
            rocket.location()
                .context());
        assertEquals(
            schedule.visits()
                .getLast()
                .place(),
            rocket.location());
        assertEquals(originalId, rocket.id());
        assertEquals(
            modules,
            rocket.modules()
                .stream()
                .map(Rocket.Module::id)
                .toList());
        assertEquals(schedule, rocket.schedule());
        assertNotNull(Rocket.deserializeNBT(rocket.serializeNBT(), definitions));
    }

    // Product contract: deliberate loss requires an uncrewed schedule and happens only at mission completion.
    @Test
    void crewCannotStartDeliberateLossButUncrewedMissionCanFinishIt() {
        var definitions = definitions();
        var rocket = assembled(definitions);
        var plan = schedule(rocket, false);
        assertTrue(plan.endsWithoutRecovery());
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(rocket.teamId(), rocket.revision(), plan));
        UUID player = UUID.randomUUID();
        assertTrue(rocket.board(player));
        assertEquals(Rocket.CommandResult.BLOCKED, rocket.start(rocket.teamId(), rocket.revision()));
        assertTrue(rocket.disembark(player));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.start(rocket.teamId(), rocket.revision()));
        var registry = new Rocket.Registry();
        registry.register(rocket);
        for (int tick = 0; tick < 2000 && rocket.isRunning(); tick++) rocket.tick(registry, tick);
        assertEquals(Rocket.Status.LOST, rocket.status());
        assertFalse(rocket.isRunning());
        assertTrue(
            rocket.modules()
                .stream()
                .allMatch(
                    module -> module.holder()
                        .kind() == Rocket.HolderKind.LOST));
        assertEquals(
            Rocket.Status.LOST,
            Rocket.deserializeNBT(rocket.serializeNBT(), definitions)
                .status());
    }

    // Product contract: departure covers every remaining allowance, not just the immediate maneuver.
    /** Product contract: fuel needed after a planned full refill is not reserved before that stop. */
    @Test
    void fullRefillAtAFutureSiloBoundsTheCurrentFuelBudget() {
        var rocket = assembled(definitions());
        var firstLeg = schedule(rocket, false);
        var departure = firstLeg.visits()
            .getFirst();
        var orbit = firstLeg.visits()
            .getLast();
        var refueling = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            departure.place(),
            List.of(VehicleSchedule.Refuel.full(UUID.randomUUID())));
        var later = new VehicleSchedule.Visit(UUID.randomUUID(), orbit.place(), List.of());
        var plan = new VehicleSchedule(List.of(departure, orbit, refueling, later), VehicleSchedule.Mode.RUN_ONCE)
            .withSafeDurability(false);
        for (var leg : plan.routes()
            .keySet()) {
            double allowance = leg.fromVisit()
                .equals(departure.id()) ? 14000
                    : leg.fromVisit()
                        .equals(refueling.id())
                            ? rocket.propulsion()
                                .usableDeltaVMps() + 1
                            : 0;
            plan = plan
                .withRouteSettings(leg, new VehicleSchedule.RouteSettings(allowance, RoutePriority.PRIORITIZE_DV));
        }
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(rocket.teamId(), rocket.revision(), plan));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.start(rocket.teamId(), rocket.revision()));
        var registry = new Rocket.Registry();
        registry.register(rocket);
        rocket.tick(registry, 0);
        assertEquals(Rocket.Status.IN_FLIGHT, rocket.status());
    }

    @Test
    void remainingAllowancesCanBlockAnIndividuallyAffordableFirstLeg() {
        var rocket = assembled(definitions());
        var plan = schedule(rocket, true);
        var first = new VehicleSchedule.Leg(
            plan.visits()
                .get(0)
                .id(),
            plan.visits()
                .get(1)
                .id());
        var second = new VehicleSchedule.Leg(
            plan.visits()
                .get(1)
                .id(),
            plan.visits()
                .get(2)
                .id());
        plan = plan.withRouteSettings(first, new VehicleSchedule.RouteSettings(14000, RoutePriority.PRIORITIZE_DV))
            .withRouteSettings(second, new VehicleSchedule.RouteSettings(30000, RoutePriority.PRIORITIZE_DV));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(rocket.teamId(), rocket.revision(), plan));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.start(rocket.teamId(), rocket.revision()));
        var registry = new Rocket.Registry();
        registry.register(rocket);
        var before = rocket.propellantAmounts();
        rocket.tick(registry, 0);
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(before, rocket.propellantAmounts());
        assertNotNull(
            rocket.scheduleView()
                .problemKey());
    }

    // Product contract: configuration changes and reload cannot alter wear already accepted for a journey.
    @Test
    void selectedPathWearIsFrozenAcrossReloadAndConfigurationChanges() {
        var definitions = definitions();
        var rocket = assembled(definitions);
        var plan = schedule(rocket, false);
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(rocket.teamId(), rocket.revision(), plan));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.start(rocket.teamId(), rocket.revision()));
        var registry = new Rocket.Registry();
        registry.register(rocket);
        rocket.tick(registry, 0);
        var view = rocket.transitView();
        assertEquals(view, Rocket.TransitView.deserializeNBT(view.serializeNBT()));
        double wear = rocket.modules()
            .getFirst()
            .part()
            .def()
            .wearPercent(
                view.journey()
                    .traveledDistanceMeters(),
                view.journey()
                    .beltDistanceMeters()
                    .values()
                    .stream()
                    .mapToDouble(Double::doubleValue)
                    .sum());
        assertTrue(wear > 0);
        double previous = ConfigRocket.wear.kilometresPerPercent;
        try {
            ConfigRocket.wear.kilometresPerPercent = previous / 10;
            registry.restoreNBT(registry.serializeNBT(), definitions);
            rocket = registry.find(rocket.id());
            for (int tick = 1; tick < 2000 && rocket.status() == Rocket.Status.IN_FLIGHT; tick++)
                rocket.tick(registry, tick);
            assertEquals(Rocket.Status.PHYSICAL, rocket.status());
            assertEquals(
                100 - wear,
                rocket.modules()
                    .getFirst()
                    .durabilityPercent(),
                1e-10);
        } finally {
            ConfigRocket.wear.kilometresPerPercent = previous;
        }
    }

    // Product contract: only the hazardous portion receives the configured multiplier.
    @Test
    void beltWearIsProportionalToDisjointExposure() {
        var module = definitions().get(91);
        double normal = module.wearPercent(1000000, 0);
        double allBelt = module.wearPercent(1000000, 1000000);
        double halfBelt = module.wearPercent(1000000, 500000);
        assertTrue(normal > 0);
        assertEquals(normal * ConfigRocket.wear.asteroidBeltMultiplier, allBelt, 1e-12);
        assertEquals((normal + allBelt) / 2, halfBelt, 1e-12);
        assertEquals(normal * 2, module.wearPercent(2000000, 0), 1e-12);
    }

    // Product contract: safe dispatch covers the complete interval, including the return landing.
    @Test
    void safeDurabilityReservesWearForTheReturnLegBeforeDeparture() {
        var definitions = definitions();
        var witness = assembled(definitions);
        var witnessPlan = schedule(witness, false);
        witness.saveSchedule(witness.teamId(), witness.revision(), witnessPlan);
        witness.start(witness.teamId(), witness.revision());
        var registry = new Rocket.Registry();
        registry.register(witness);
        witness.tick(registry, 0);
        double distanceKm = witness.transitView()
            .journey()
            .traveledDistanceMeters() / 1000;
        double previous = ConfigRocket.wear.kilometresPerPercent;
        try {
            ConfigRocket.wear.kilometresPerPercent = distanceKm
                / (0.6 * (100 - ConfigRocket.flight.minimumDurabilityReservePercent));
            var oneWay = assembled(definitions);
            var returning = assembled(definitions);
            for (var rocket : List.of(oneWay, returning)) {
                var plan = schedule(rocket, rocket == returning).withSafeDurability(true);
                assertEquals(
                    Rocket.CommandResult.APPLIED,
                    rocket.saveSchedule(rocket.teamId(), rocket.revision(), plan));
                assertEquals(Rocket.CommandResult.APPLIED, rocket.start(rocket.teamId(), rocket.revision()));
                registry.register(rocket);
                rocket.tick(registry, 0);
            }
            assertEquals(Rocket.Status.IN_FLIGHT, oneWay.status());
            assertEquals(Rocket.Status.PHYSICAL, returning.status());
            assertEquals(Map.of(FUEL, 1000L, OXIDIZER, 2000L), returning.propellantAmounts());
            assertNotNull(
                returning.scheduleView()
                    .problemKey());
        } finally {
            ConfigRocket.wear.kilometresPerPercent = previous;
        }
    }

    // Bug regression: a visit at the actual assembly place advances without another ascent or fuel debit.
    @Test
    void consecutiveVisitsAtTheCurrentPlaceDoNotLaunchTheVehicle() {
        var rocket = assembled(definitions());
        var place = rocket.location();
        var first = new VehicleSchedule.Visit(UUID.randomUUID(), place, List.of());
        var second = new VehicleSchedule.Visit(UUID.randomUUID(), place, List.of());
        var plan = new VehicleSchedule(List.of(first, second), VehicleSchedule.Mode.RUN_ONCE);
        var leg = plan.routes()
            .keySet()
            .iterator()
            .next();
        plan = plan.withRouteSettings(leg, new VehicleSchedule.RouteSettings(0, RoutePriority.PRIORITIZE_DV));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(rocket.teamId(), rocket.revision(), plan));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.start(rocket.teamId(), rocket.revision()));
        var registry = new Rocket.Registry();
        registry.register(rocket);
        var fuel = rocket.propellantAmounts();
        rocket.tick(registry, 0);
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(
            second.id(),
            rocket.scheduleView()
                .activeVisitId());
        rocket.tick(registry, 1);
        assertFalse(rocket.isRunning());
        assertEquals(fuel, rocket.propellantAmounts());
    }

    // Integration contract: the basic vehicle pays whole native mixture units through lunar landing and reload.
    @Test
    void basicVehiclePaysEveryLunarManeuverFromItsActualTanks() {
        var config = ConfigRocket.basic;
        var definitions = new RocketPartRegistry();
        var design = new RocketBlueprint();
        var kinds = List.of(
            RocketPartDef.Kind.CAPSULE,
            RocketPartDef.Kind.FUEL_TANK,
            RocketPartDef.Kind.ENGINE,
            RocketPartDef.Kind.CARGO);
        var masses = List
            .of(config.capsuleDryMassKg, config.tankDryMassKg, config.engineDryMassKg, config.cargoDryMassKg);
        for (int i = 0; i < kinds.size(); i++) {
            var kind = kinds.get(i);
            var storage = new RocketPartDef.Storage(
                kind == RocketPartDef.Kind.CARGO ? config.itemCargoSlots : 0,
                0,
                kind == RocketPartDef.Kind.FUEL_TANK
                    ? Map.of(FUEL, (long) config.fuelCapacity, OXIDIZER, (long) config.oxidizerCapacity)
                    : Map.of());
            var engine = kind == RocketPartDef.Kind.ENGINE
                ? new RocketPartDef.Engine(
                    config.thrustNewtons,
                    config.exhaustVelocityMps,
                    Map.of(
                        FUEL,
                        new RocketPartDef.Propellant(config.fuelVolumeParts, config.fuelKilogramsPerUnit),
                        OXIDIZER,
                        new RocketPartDef.Propellant(config.oxidizerVolumeParts, config.oxidizerKilogramsPerUnit)))
                : null;
            var definition = new RocketPartDef(
                93 + i,
                "basic_lunar_test_" + kind.name(),
                1,
                1,
                masses.get(i),
                null,
                kind,
                0,
                0,
                storage,
                engine,
                null,
                null);
            definitions.register(definition, RocketPartCategory.STRUCTURAL);
            assertTrue(design.addPart(new RocketPartInstance(definition, 0, i, 0, false)));
        }
        var rocket = Rocket.construct(
            UUID.randomUUID(),
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            UUID.randomUUID(),
            design);
        for (var slot : rocket.buildSlots()) {
            assertTrue(
                rocket.acceptProduced(
                    Rocket.Module.produced(
                        rocket.id(),
                        slot,
                        100,
                        new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId()))));
        }
        assertTrue(rocket.assemble(rocket.assemblySiloId()));
        assertEquals(config.fuelCapacity, rocket.refuel(FUEL, config.fuelCapacity, false));
        assertEquals(config.oxidizerCapacity, rocket.refuel(OXIDIZER, config.oxidizerCapacity, false));
        var lunarSurface = new VehicleSchedule.Place(
            CelestialObjectKey.registered(CelestialObjectId.MOON),
            VehicleSchedule.Context.SURFACE,
            null,
            new VehicleSchedule.SurfaceSite(DimensionEnum.MOON.getId(), 100, 64, 100, rocket.footprint()));
        var saved = rocket.schedule();
        assertTrue(rocket.board(UUID.randomUUID()));
        var id = rocket.id();
        var moduleIds = rocket.modules()
            .stream()
            .map(Rocket.Module::id)
            .toList();
        var registry = new Rocket.Registry();
        registry.register(rocket);
        assertEquals(
            Rocket.CommandResult.APPLIED,
            registry.acceptManualLanding(
                rocket.id(),
                rocket.teamId(),
                rocket.revision(),
                lunarSurface,
                new VehicleSchedule.RouteSettings(
                    rocket.propulsion()
                        .usableDeltaVMps(),
                    RoutePriority.PRIORITIZE_DV)));
        rocket.tick(registry, 0);
        assertNotNull(
            rocket.scheduleView()
                .launchWarning());
        assertEquals(Rocket.CommandResult.APPLIED, rocket.reviewLaunchRisk(rocket.teamId(), rocket.revision(), true));
        rocket.tick(registry, 0);
        assertEquals(Rocket.Status.IN_FLIGHT, rocket.status());
        for (int tick = 1; tick < 4000 && rocket.isRunning(); tick++) {
            rocket.tick(registry, tick);
            if (tick == 5) {
                registry.restoreNBT(registry.serializeNBT(), definitions);
                rocket = registry.find(id);
            }
        }
        assertFalse(rocket.isRunning());
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(lunarSurface, rocket.location());
        assertEquals(saved, rocket.schedule());
        assertEquals(
            moduleIds,
            rocket.modules()
                .stream()
                .map(Rocket.Module::id)
                .toList());
        assertEquals(
            1,
            rocket.passengers()
                .size());
        long remainingFuel = rocket.propellantAmounts()
            .get(FUEL);
        assertTrue(remainingFuel > 0 && remainingFuel < config.fuelCapacity);
        assertEquals(
            remainingFuel * config.oxidizerVolumeParts / config.fuelVolumeParts,
            rocket.propellantAmounts()
                .get(OXIDIZER)
                .doubleValue(),
            1e-9);
        assertEquals(
            rocket.scheduleView(),
            Rocket.ScheduleView.deserializeNBT(
                rocket.scheduleView()
                    .serializeNBT()));
    }

    // Product contract: a crewed departure with insufficient onboard return supplies waits before charging fuel.
    @Test
    void crewedOneWayRiskWaitsForAcknowledgementBeforeDeparture() {
        var definitions = definitions(3000);
        var rocket = assembled(definitions);
        var source = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            new VehicleSchedule.Place(rocket.bodyKey(), VehicleSchedule.Context.SURFACE, null),
            List.of());
        var destination = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            new VehicleSchedule.Place(
                rocket.bodyKey(),
                VehicleSchedule.Context.ORBIT,
                new VehicleSchedule.Group(
                    "risk_test",
                    com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint.Kind.DOCK,
                    null)),
            List.of());
        var plan = new VehicleSchedule(List.of(source, destination), VehicleSchedule.Mode.RUN_ONCE);
        plan = plan.withRouteSettings(
            new VehicleSchedule.Leg(source.id(), destination.id()),
            new VehicleSchedule.RouteSettings(
                rocket.propulsion()
                    .usableDeltaVMps(),
                RoutePriority.PRIORITIZE_DV));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(rocket.teamId(), rocket.revision(), plan));
        assertTrue(rocket.board(UUID.randomUUID()));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.start(rocket.teamId(), rocket.revision()));
        var registry = new Rocket.Registry();
        registry.register(rocket);
        var fuel = rocket.propellantAmounts();
        rocket.tick(registry, 0);
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(fuel, rocket.propellantAmounts());
        assertNotNull(
            rocket.scheduleView()
                .problemKey());
        var warning = rocket.scheduleView()
            .launchWarning();
        assertNotNull(warning);
        assertTrue(warning.remainingDeltaVMps() < warning.outboundDeltaVMps());
        var vehicleId = rocket.id();
        registry.restoreNBT(registry.serializeNBT(), definitions);
        rocket = registry.find(vehicleId);
        assertEquals(
            warning,
            rocket.scheduleView()
                .launchWarning());
        var unattended = Rocket.deserializeNBT(rocket.serializeNBT(), definitions);
        var unattendedRegistry = new Rocket.Registry();
        unattendedRegistry.register(unattended);
        assertTrue(
            unattended.disembark(
                unattended.passengers()
                    .iterator()
                    .next()));
        unattended.tick(unattendedRegistry, 0);
        assertEquals(Rocket.Status.IN_FLIGHT, unattended.status());
        assertEquals(
            Rocket.CommandResult.FORBIDDEN,
            rocket.reviewLaunchRisk(UUID.randomUUID(), rocket.revision(), true));
        assertEquals(Rocket.CommandResult.STALE, rocket.reviewLaunchRisk(rocket.teamId(), rocket.revision() - 1, true));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.reviewLaunchRisk(rocket.teamId(), rocket.revision(), false));
        assertNull(
            rocket.scheduleView()
                .launchWarning());
        for (int tick = 1; tick < 30; tick++) rocket.tick(registry, tick);
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(fuel, rocket.propellantAmounts());
        assertEquals(Rocket.CommandResult.APPLIED, rocket.start(rocket.teamId(), rocket.revision()));
        rocket.tick(registry, 0);
        assertNotNull(
            rocket.scheduleView()
                .launchWarning());
        assertEquals(Rocket.CommandResult.APPLIED, rocket.reviewLaunchRisk(rocket.teamId(), rocket.revision(), true));
        var changed = new VehicleSchedule.Visit(
            destination.id(),
            new VehicleSchedule.Place(
                rocket.bodyKey(),
                VehicleSchedule.Context.ORBIT,
                new VehicleSchedule.Group(
                    "changed_risk_target",
                    com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint.Kind.DOCK,
                    null)),
            List.of());
        assertEquals(
            Rocket.CommandResult.APPLIED,
            rocket.saveSchedule(
                rocket.teamId(),
                rocket.revision(),
                rocket.schedule()
                    .withVisit(changed)));
        rocket.tick(registry, 0);
        assertNotNull(
            rocket.scheduleView()
                .launchWarning());
        assertEquals(Rocket.CommandResult.APPLIED, rocket.reviewLaunchRisk(rocket.teamId(), rocket.revision(), true));
        registry.restoreNBT(registry.serializeNBT(), definitions);
        rocket = registry.find(vehicleId);
        rocket.tick(registry, 0);
        assertEquals(Rocket.Status.IN_FLIGHT, rocket.status());
        assertNull(
            rocket.scheduleView()
                .launchWarning());
    }

    private static VehicleSchedule schedule(Rocket rocket, boolean returns) {
        var ground = new VehicleSchedule.Visit(UUID.randomUUID(), rocket.location(), List.of());
        var orbit = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            new VehicleSchedule.Place(rocket.bodyKey(), VehicleSchedule.Context.ORBIT, null),
            List.of());
        var visits = new java.util.ArrayList<>(List.of(ground, orbit));
        if (returns) {
            var facility = new AutomatedFacility(
                CelestialAsset.ID.create(),
                rocket.bodyKey(),
                CelestialAsset.Kind.AUTOMATED_OUTPOST,
                Buildable.Status.OPERATIONAL);
            var bay = FacilityModuleKind.LANDING_BAY
                .create(StationTileCoord.of(1, 0), FacilityModuleKind.LANDING_BAY.defaultShape(), ModuleTier.NONE);
            bay.updateStatus(Buildable.Status.OPERATIONAL);
            com.gtnewhorizons.galaxia.registry.outpost.FacilityTestFixtures.addModule(facility, bay);
            facility.setEnergyStored(bay.powerDrawEuPerTick());
            facility.tick();
            assertTrue(facility.availableLandingSlots() > 0);
            assertEquals(0, facility.getEnergyStored());
            CelestialAssetStore.SERVER.registerAssetInternal(rocket.teamId(), facility);
            PREPARED_RETURNS.add(facility.assetId);
            var destination = new VehicleSchedule.Place(
                rocket.bodyKey(),
                VehicleSchedule.Context.SURFACE,
                new VehicleSchedule.Exact(facility.assetId.id(), CelestialAsset.Endpoint.Kind.LANDING_PAD));
            visits.add(new VehicleSchedule.Visit(UUID.randomUUID(), destination, List.of()));
        }
        var plan = new VehicleSchedule(visits, VehicleSchedule.Mode.RUN_ONCE).withSafeDurability(false);
        for (var leg : plan.routes()
            .keySet())
            plan = plan.withRouteSettings(leg, new VehicleSchedule.RouteSettings(14000, RoutePriority.PRIORITIZE_DV));
        return plan;
    }

    private static RocketPartRegistry definitions() {
        return definitions(10000);
    }

    private static RocketPartRegistry definitions(double exhaustVelocity) {
        var definitions = new RocketPartRegistry();
        definitions.register(
            new RocketPartDef(
                91,
                "journey_test_engine",
                1,
                1,
                100,
                null,
                RocketPartDef.Kind.ENGINE,
                0,
                0,
                new RocketPartDef.Storage(0, 0, Map.of(FUEL, 1000L, OXIDIZER, 2000L)),
                new RocketPartDef.Engine(
                    1000000,
                    exhaustVelocity,
                    Map.of(FUEL, new RocketPartDef.Propellant(1, 1), OXIDIZER, new RocketPartDef.Propellant(2, 1))),
                null,
                null),
            RocketPartCategory.LIQUID_ENGINES);
        return definitions;
    }

    private static Rocket assembled(RocketPartRegistry definitions) {
        var blueprint = new RocketBlueprint();
        assertTrue(blueprint.addPart(new RocketPartInstance(definitions.get(91), 0, 0, 0, false)));
        var rocket = Rocket.construct(
            UUID.randomUUID(),
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            UUID.randomUUID(),
            blueprint);
        assertTrue(
            rocket.acceptProduced(
                Rocket.Module.produced(
                    rocket.id(),
                    rocket.buildSlots()
                        .getFirst(),
                    100,
                    new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId()))));
        assertTrue(rocket.assemble(rocket.assemblySiloId()));
        assertNotNull(rocket.claimProjection(UUID.randomUUID(), new Rocket.PhysicalPosition(0, 0, 64, 0, 0)));
        assertEquals(1000, rocket.refuel(FUEL, 1000, false));
        assertEquals(2000, rocket.refuel(OXIDIZER, 2000, false));
        return rocket;
    }
}
