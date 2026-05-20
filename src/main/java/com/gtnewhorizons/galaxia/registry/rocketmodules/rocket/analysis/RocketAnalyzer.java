package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.CapsulePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.DecouplerPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.LanderPartDef;

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

        List<RocketPartInstance> sorted = new ArrayList<>(parts);
        sorted.sort(Comparator.comparingInt(RocketPartInstance::y));

        for (RocketPartInstance p : sorted) {
            if (p.def() instanceof DecouplerPartDef def) {
                currentStage = Math.max(currentStage, def.decouplerStage());
            }
            stage.put(p, currentStage);
        }
        return stage;
    }

    private static boolean checkViability(RocketBlueprint blueprint, List<RocketStage> stages) {
        if (stages.isEmpty()) return false;

        boolean hasCommand = blueprint.getParts()
            .stream()
            .anyMatch(p -> p.def() instanceof CapsulePartDef || p.def() instanceof LanderPartDef);

        if (!hasCommand) return false;

        return stages.get(0)
            .canLaunch(calculatePayloadMass(blueprint));
    }

    private static double calculatePayloadMass(RocketBlueprint bp) {
        return bp.getParts()
            .stream()
            .filter(p -> p.def() instanceof CapsulePartDef || p.def() instanceof LanderPartDef)
            .mapToDouble(
                p -> p.def()
                    .weight())
            .sum();
    }
}
