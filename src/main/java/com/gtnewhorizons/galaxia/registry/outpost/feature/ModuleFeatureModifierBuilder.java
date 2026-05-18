package com.gtnewhorizons.galaxia.registry.outpost.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ModuleFeatureModifierBuilder {

    private int buildSpeedModifierPercent;
    private int upkeepMultiplierPercent = 100;
    private int powerDrawMultiplierPercent = 100;
    private final List<FeatureContribution> contributions = new ArrayList<>();

    public void addBuildSpeedModifierPercent(int modifierPercent) {
        buildSpeedModifierPercent += modifierPercent;
    }

    public void minUpkeepMultiplierPercent(int multiplierPercent) {
        upkeepMultiplierPercent = Math.min(upkeepMultiplierPercent, multiplierPercent);
    }

    public void minPowerDrawMultiplierPercent(int multiplierPercent) {
        powerDrawMultiplierPercent = Math.min(powerDrawMultiplierPercent, multiplierPercent);
    }

    public void addContribution(FeatureContribution contribution) {
        if (contribution != null) contributions.add(contribution);
    }

    public ModuleFeatureModifiers build(Map<PlanetaryFeatureKey, Integer> coveredTiles) {
        return new ModuleFeatureModifiers(
            buildSpeedModifierPercent,
            upkeepMultiplierPercent,
            powerDrawMultiplierPercent,
            coveredTiles,
            contributions);
    }
}
