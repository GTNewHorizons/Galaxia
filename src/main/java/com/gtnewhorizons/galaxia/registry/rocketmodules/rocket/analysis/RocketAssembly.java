package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import java.util.List;

public record RocketAssembly(RocketBlueprint blueprint, List<RocketStage> stages, boolean viable) {
    public double getTotalDeltaV() {
        return stages.stream().mapToDouble(RocketStage::getDeltaV).sum();
    }
    public int getStageCount() { return stages.size(); }
    public double getTotalMass() {
        return stages.stream().mapToDouble(RocketStage::getTotalMass).sum();
    }
}
