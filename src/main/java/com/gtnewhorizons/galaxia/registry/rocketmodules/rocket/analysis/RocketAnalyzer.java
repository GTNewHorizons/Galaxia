package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartType;
import java.util.*;

public class RocketAnalyzer {
    public static RocketAssembly analyze(RocketBlueprint blueprint) {
        List<RocketPartInstance> allParts = blueprint.getParts();
        if (allParts.isEmpty()) return new RocketAssembly(blueprint, List.of(), false);

        Map<RocketPartInstance, Integer> stageMap = buildStageMap(allParts);
        int maxStage = stageMap.values().stream().max(Integer::compareTo).orElse(0);
        List<RocketStage> stages = new ArrayList<>();
        for (int i = 0; i <= maxStage; i++) stages.add(new RocketStage(i));

        for (RocketPartInstance part : allParts) {
            stages.get(stageMap.get(part)).addPart(part);
        }

        boolean viable = checkViability(blueprint, stages);
        return new RocketAssembly(blueprint, stages, viable);
    }

    private static Map<RocketPartInstance, Integer> buildStageMap(List<RocketPartInstance> parts) {
        Map<RocketPartInstance, Integer> stage = new HashMap<>();
        Deque<RocketPartInstance> stack = new ArrayDeque<>();
        Set<RocketPartInstance> visited = new HashSet<>();

        for (RocketPartInstance part : parts) {
            if (part.def().type() == RocketPartType.CAPSULE || part.def().type() == RocketPartType.LANDER) {
                stage.put(part, 0);
                stack.push(part);
                visited.add(part);
                break;
            }
        }
        if (stack.isEmpty()) return stage;

        while (!stack.isEmpty()) {
            RocketPartInstance current = stack.pop();
            int currentStage = stage.get(current);
            for (RocketPartInstance neighbor : findConnected(current, parts)) {
                if (visited.contains(neighbor)) continue;
                visited.add(neighbor);
                int neighborStage = currentStage;
                if (neighbor.def().type() == RocketPartType.DECOUPLER && neighbor.def().decouplerStage() >= 0) {
                    neighborStage = neighbor.def().decouplerStage();
                }
                stage.put(neighbor, neighborStage);
                stack.push(neighbor);
            }
        }
        return stage;
    }

    private static List<RocketPartInstance> findConnected(RocketPartInstance part, List<RocketPartInstance> allParts) {
        List<RocketPartInstance> connections = new ArrayList<>();
        int px = part.x(), py = part.y(), pz = part.z();
        int ph = part.def().getHeightCells();
        int pw = part.def().getWidthCells();

        for (RocketPartInstance other : allParts) {
            if (part == other) continue;
            int ox = other.x(), oy = other.y(), oz = other.z();
            int oh = other.def().getHeightCells();
            int ow = other.def().getWidthCells();

            boolean vertical = (px == ox || (px < ox + ow && ox < px + pw)) && pz == oz &&
                (py + ph == oy || oy + oh == py);
            boolean radial = part.isRadial() == other.isRadial() && py == oy && pz == oz &&
                Math.abs(px - ox) <= (pw + ow) / 2 + 1;
            if (vertical || radial) connections.add(other);
        }
        return connections;
    }

    private static boolean checkViability(RocketBlueprint blueprint, List<RocketStage> stages) {
        if (stages.isEmpty()) return false;
        double commandWeight = 0;
        for (RocketPartInstance p : blueprint.getParts()) {
            if (p.def().type() == RocketPartType.CAPSULE || p.def().type() == RocketPartType.LANDER)
                commandWeight = p.def().weight();
        }
        if (commandWeight == 0) return false;
        return stages.getFirst().canLaunch(commandWeight);
    }
}
