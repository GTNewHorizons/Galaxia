package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RocketAnalyzer {

    private RocketAnalyzer() {}

    public static RocketAssembly analyze(RocketBlueprint blueprint) {
        if (blueprint.isEmpty()) {
            return new RocketAssembly(blueprint, List.of(), false);
        }

        List<RocketPartInstance> parts = blueprint.getParts();
        Map<RocketPartInstance, Integer> stageMap = buildStageMap(parts);

        List<RocketStage> stages = new ArrayList<>();
        for (int stageNum = 0; stageNum < 10; stageNum++) {
            RocketStage stage = new RocketStage(stageNum);
            boolean hasParts = false;
            for (RocketPartInstance p : parts) {
                if (stageMap.getOrDefault(p, -1) == stageNum) {
                    stage.addPart(p);
                    hasParts = true;
                }
            }
            if (hasParts) stages.add(stage);
        }

        boolean viable = checkViability(blueprint, stages);
        return new RocketAssembly(blueprint, stages, viable);
    }

    private static Map<RocketPartInstance, Integer> buildStageMap(List<RocketPartInstance> parts) {
        Map<RocketPartInstance, Integer> stage = new HashMap<>();
        int currentStage = 0;
        for (RocketPartInstance p : parts) {
            if (p.def().type() == RocketPartType.DECOUPLER) {
                currentStage = Math.max(currentStage, p.def().decouplerStage());
            }
            stage.put(p, currentStage);
        }
        return stage;
    }

    private static boolean checkViability(RocketBlueprint blueprint, List<RocketStage> stages) {
        if (stages.isEmpty()) return false;

        boolean hasCommand = blueprint.getParts().stream()
            .anyMatch(p -> p.def().type() == RocketPartType.CAPSULE || p.def().type() == RocketPartType.LANDER);

        if (!hasCommand) return false;

        return stages.get(0).canLaunch(calculatePayloadMass(blueprint));
    }

    private static double calculatePayloadMass(RocketBlueprint bp) {
        return bp.getParts().stream()
            .filter(p -> p.def().type() == RocketPartType.CAPSULE || p.def().type() == RocketPartType.LANDER)
            .mapToDouble(p -> p.def().weight())
            .sum();
    }
}
