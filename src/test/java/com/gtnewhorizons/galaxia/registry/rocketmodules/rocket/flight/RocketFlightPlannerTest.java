package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAnalyzer;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAssembly;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.FuelTankFrame;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuel;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.CapsulePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.DecouplerPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.EnginePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FuelTankPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.ModuleRecipe;

final class RocketFlightPlannerTest {

    private static final RocketFuel FUEL = RocketFuel.builder("test")
        .density(1.0)
        .engine(100_000, 300, 500, ModuleRecipe.EMPTY)
        .build();

    private static final CapsulePartDef CAPSULE = new CapsulePartDef("capsule", 3, 3, 500, null);
    private static final DecouplerPartDef DECOUPLER = new DecouplerPartDef("decoupler", 3, 1, 100, null);
    private static final EnginePartDef ENGINE = new EnginePartDef(FUEL);
    private static final FuelTankPartDef TANK = new FuelTankPartDef(FuelTankFrame.WIDE_3, FUEL);

    /** Capsule, upper tank and engine, decoupler, lower tank and engine */
    private static RocketBlueprint twoStageRocket() {
        RocketBlueprint blueprint = new RocketBlueprint();
        blueprint.addPart(new RocketPartInstance(CAPSULE, 1, 0, 0, false));
        blueprint.addPart(new RocketPartInstance(TANK, 0, 3, 0, false));
        blueprint.addPart(new RocketPartInstance(ENGINE, 1, 6, 0, false));
        blueprint.addPart(new RocketPartInstance(DECOUPLER, 1, 10, 0, false));
        blueprint.addPart(new RocketPartInstance(TANK, 0, 11, 0, false));
        blueprint.addPart(new RocketPartInstance(ENGINE, 1, 14, 0, false));
        return blueprint;
    }

    @Test
    void decouplerSplitsTheRocketIntoStages() {
        RocketAssembly assembly = RocketAnalyzer.analyze(twoStageRocket());

        assertEquals(2, assembly.getStageCount());
        assertTrue(assembly.viable(), "Problems: " + assembly.problems());
        assertEquals(
            3,
            assembly.stages()
                .get(1)
                .getParts()
                .size(),
            "The decoupler belongs to the lower stage");
    }

    @Test
    void shortHopKeepsEveryStage() {
        RocketAssembly assembly = RocketAnalyzer.analyze(twoStageRocket());
        RocketLoadout full = RocketAnalyzer.fullLoadout(assembly.blueprint());

        RocketFlightPlanner.FlightPlan plan = RocketFlightPlanner.plan(assembly, full, 100);

        assertTrue(plan.reachable());
        assertEquals(1, plan.lowestSurvivingStage(), "The first stage still has fuel left and arrives");
        assertEquals(
            6,
            plan.survivingParts(assembly)
                .size());
    }

    @Test
    void longFlightDropsTheSpentFirstStage() {
        RocketAssembly assembly = RocketAnalyzer.analyze(twoStageRocket());
        RocketLoadout full = RocketAnalyzer.fullLoadout(assembly.blueprint());
        double total = assembly.getDeltaV(full);
        // More than the first stage alone can give, less than both together
        double required = total * 0.8;

        RocketFlightPlanner.FlightPlan plan = RocketFlightPlanner.plan(assembly, full, required);

        assertTrue(plan.reachable());
        assertEquals(0, plan.lowestSurvivingStage(), "Only the upper stage arrives");
        assertEquals(
            3,
            plan.survivingParts(assembly)
                .size());
        long remaining = plan.remaining()
            .getTotalFuel(assembly.blueprint());
        assertTrue(remaining < full.getTotalFuel(assembly.blueprint()), "Fuel was burned");
    }

    @Test
    void notEnoughDeltaVIsRefused() {
        RocketAssembly assembly = RocketAnalyzer.analyze(twoStageRocket());
        RocketLoadout full = RocketAnalyzer.fullLoadout(assembly.blueprint());

        RocketFlightPlanner.FlightPlan plan = RocketFlightPlanner.plan(assembly, full, assembly.getDeltaV(full) * 1.5);

        assertFalse(plan.reachable());
    }

    @Test
    void emptyTanksGiveNoDeltaV() {
        RocketAssembly assembly = RocketAnalyzer.analyze(twoStageRocket());

        assertEquals(0, assembly.getDeltaV(new RocketLoadout()), 1e-9);
    }
}
