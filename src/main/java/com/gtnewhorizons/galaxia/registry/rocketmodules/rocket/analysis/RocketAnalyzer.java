package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.Kind;

public final class RocketAnalyzer {

    /** Structural analysis only. Actual flight feasibility uses the supplied vehicle state. */
    public record Analysis(RocketBlueprint blueprint, List<Stage> stages, boolean viable) {

        public Analysis {
            stages = List.copyOf(stages);
        }

        public int getStageCount() {
            return stages.size();
        }

        public double getTotalDryMassKg() {
            return stages.stream()
                .flatMap(
                    stage -> stage.parts()
                        .stream())
                .mapToDouble(
                    part -> part.def()
                        .dryMassKg())
                .sum();
        }
    }

    public record Stage(int number, List<RocketPartInstance> parts) {

        public Stage {
            parts = List.copyOf(parts);
        }
    }

    private RocketAnalyzer() {}

    public static Analysis analyze(RocketBlueprint blueprint) {
        if (blueprint.isEmpty()) {
            return new Analysis(blueprint, List.of(), false);
        }

        List<RocketPartInstance> parts = blueprint.getParts();
        Map<RocketPartInstance, Integer> stageMap = buildStageMap(parts);

        List<Stage> stages = new ArrayList<>();
        for (int stageNum = 0; stageNum < 10; stageNum++) {
            List<RocketPartInstance> stageParts = new ArrayList<>();
            for (RocketPartInstance p : parts) {
                if (stageMap.getOrDefault(p, -1) == stageNum) {
                    stageParts.add(p);
                }
            }
            if (!stageParts.isEmpty()) stages.add(new Stage(stageNum, stageParts));
        }

        boolean viable = checkViability(blueprint, stages);
        return new Analysis(blueprint, stages, viable);
    }

    private static Map<RocketPartInstance, Integer> buildStageMap(List<RocketPartInstance> parts) {
        Map<RocketPartInstance, Integer> stage = new HashMap<>();
        int currentStage = 0;

        List<RocketPartInstance> sorted = new ArrayList<>(parts);
        sorted.sort(Comparator.comparingInt(RocketPartInstance::y));

        for (RocketPartInstance p : sorted) {
            if (p.def()
                .kind() == Kind.DECOUPLER) {
                currentStage = Math.max(
                    currentStage,
                    p.def()
                        .decouplerStage());
            }
            stage.put(p, currentStage);
        }
        return stage;
    }

    private static boolean checkViability(RocketBlueprint blueprint, List<Stage> stages) {
        if (stages.isEmpty()) return false;

        boolean hasCommand = blueprint.getParts()
            .stream()
            .anyMatch(
                p -> p.def()
                    .kind() == Kind.CAPSULE
                    || p.def()
                        .kind() == Kind.LANDER);

        if (!hasCommand) return false;

        return stages.get(0)
            .parts()
            .stream()
            .anyMatch(
                part -> part.def()
                    .engine() != null);
    }
}
