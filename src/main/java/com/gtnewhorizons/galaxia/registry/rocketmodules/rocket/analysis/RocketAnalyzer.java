package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketFlightPlanner;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketLoadout;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.CapsulePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.DecouplerPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FuelTankPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.LanderPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.StoragePartDef;

public final class RocketAnalyzer {

    public static final String PROBLEM_PREFIX = "galaxia.rocket_editor.problem.";

    private RocketAnalyzer() {}

    public static RocketAssembly analyze(RocketBlueprint blueprint) {
        if (blueprint.isEmpty()) {
            return new RocketAssembly(blueprint, List.of(), List.of(PROBLEM_PREFIX + "empty"));
        }

        Map<RocketPartInstance, Integer> stageMap = buildStageMap(blueprint.getParts());
        int stageCount = 1 + stageMap.values()
            .stream()
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0);

        List<RocketStage> stages = new ArrayList<>();
        for (int stageNum = 0; stageNum < stageCount; stageNum++) {
            RocketStage stage = new RocketStage(stageNum);
            for (RocketPartInstance p : blueprint.getParts()) {
                if (stageMap.get(p) == stageNum) stage.addPart(p);
            }
            if (!stage.getParts()
                .isEmpty()) stages.add(stage);
        }

        return new RocketAssembly(blueprint, stages, findProblems(blueprint, stages));
    }

    /**
     * Assigns stages top to bottom. Every decoupler starts a new stage and belongs to it, so it is dropped together
     * with everything below it
     */
    private static Map<RocketPartInstance, Integer> buildStageMap(List<RocketPartInstance> parts) {
        Map<RocketPartInstance, Integer> stage = new HashMap<>();
        int currentStage = 0;

        List<RocketPartInstance> sorted = new ArrayList<>(parts);
        sorted.sort(
            Comparator.comparingInt(RocketPartInstance::y)
                .thenComparingInt(p -> p.def() instanceof DecouplerPartDef ? 0 : 1));

        for (RocketPartInstance p : sorted) {
            if (p.def() instanceof DecouplerPartDef) currentStage++;
            stage.put(p, currentStage);
        }
        return stage;
    }

    private static List<String> findProblems(RocketBlueprint blueprint, List<RocketStage> stages) {
        List<String> problems = new ArrayList<>();

        boolean hasPayload = blueprint.getParts()
            .stream()
            .anyMatch(
                p -> p.def() instanceof CapsulePartDef || p.def() instanceof LanderPartDef
                    || p.def() instanceof StoragePartDef);
        if (!hasPayload) problems.add(PROBLEM_PREFIX + "no_payload");

        RocketStage first = stages.getLast();
        if (!first.hasEngines()) problems.add(PROBLEM_PREFIX + "no_engine");

        for (RocketStage stage : stages) {
            for (RocketPartInstance tank : stage.getTanks()) {
                if (!stage.canBurn(tank)) {
                    problems.add(PROBLEM_PREFIX + "unburnable_fuel");
                    break;
                }
            }
        }

        // Designs are judged with full tanks at Earth gravity
        RocketLoadout full = fullLoadout(blueprint);
        if (first.hasEngines() && !RocketFlightPlanner.canLiftOff(stages, full, 1.0)) {
            problems.add(PROBLEM_PREFIX + "too_heavy");
        }
        return problems;
    }

    /**
     * Builds a loadout with every tank filled to capacity, used to rate designs before they are fueled
     *
     * @param blueprint The rocket design
     *
     * @return A loadout with full tanks and empty holds
     */
    public static RocketLoadout fullLoadout(RocketBlueprint blueprint) {
        RocketLoadout loadout = new RocketLoadout();
        for (RocketPartInstance part : blueprint.getParts()) {
            if (part.def() instanceof FuelTankPartDef tank) loadout.setFuel(part, tank.fuelCapacity());
        }
        return loadout;
    }
}
