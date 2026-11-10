package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis;

import java.util.List;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketFlightPlanner;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketLoadout;

/**
 * A blueprint split into stages, plus everything that stops it from flying
 *
 * @param blueprint The analysed design
 * @param stages    Stages ordered top (0) to bottom
 * @param problems  Lang keys describing why the design cannot fly, empty when viable
 */
public record RocketAssembly(RocketBlueprint blueprint, List<RocketStage> stages, List<String> problems) {

    public boolean viable() {
        return problems.isEmpty();
    }

    /** Delta-v in m/s with every tank full, ignoring cargo */
    public double getTotalDeltaV() {
        return getDeltaV(RocketAnalyzer.fullLoadout(blueprint));
    }

    /** Delta-v in m/s with the given fuel and cargo */
    public double getDeltaV(RocketLoadout loadout) {
        return RocketFlightPlanner.availableDeltaV(stages, loadout);
    }

    public int getStageCount() {
        return stages.size();
    }

    /** Dry mass in kg */
    public double getTotalMass() {
        return stages.stream()
            .mapToDouble(RocketStage::getDryMass)
            .sum();
    }

    public double getTotalThrust() {
        return stages.stream()
            .mapToDouble(RocketStage::getTotalThrust)
            .sum();
    }

    public static RocketAssembly fromBlueprint(RocketBlueprint blueprint) {
        return RocketAnalyzer.analyze(blueprint);
    }
}
