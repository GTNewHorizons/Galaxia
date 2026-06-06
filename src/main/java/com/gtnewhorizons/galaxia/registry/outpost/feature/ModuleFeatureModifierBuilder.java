package com.gtnewhorizons.galaxia.registry.outpost.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ModuleFeatureModifierBuilder {

    private int buildSpeedModifierPercent;
    private int upkeepMultiplierPercent = 100;
    private int powerDrawReductionMultiplierPercent = 100;
    private int powerDrawIncreaseMultiplierPercent = 100;
    private final List<FeatureContribution> contributions = new ArrayList<>();

    public void addBuildSpeedModifierPercent(int modifierPercent) {
        buildSpeedModifierPercent += modifierPercent;
    }

    public void minUpkeepMultiplierPercent(int multiplierPercent) {
        upkeepMultiplierPercent = Math.min(upkeepMultiplierPercent, multiplierPercent);
    }

    public void minPowerDrawMultiplierPercent(int multiplierPercent) {
        powerDrawReductionMultiplierPercent = Math.min(powerDrawReductionMultiplierPercent, multiplierPercent);
    }

    public void multiplyPowerDrawMultiplierPercent(int multiplierPercent) {
        powerDrawIncreaseMultiplierPercent = powerDrawIncreaseMultiplierPercent * Math.max(0, multiplierPercent) / 100;
    }

    public void addContribution(FeatureContribution contribution) {
        if (contribution != null) contributions.add(contribution);
    }

    public ModuleFeatureModifiers build(Map<PlanetaryFeatureKey, Integer> coveredTiles) {
        return new ModuleFeatureModifiers(
            buildSpeedModifierPercent,
            upkeepMultiplierPercent,
            powerDrawMultiplierPercent(),
            coveredTiles,
            contributions);
    }

    private int powerDrawMultiplierPercent() {
        return (powerDrawReductionMultiplierPercent * powerDrawIncreaseMultiplierPercent + 99) / 100;
    }
}
