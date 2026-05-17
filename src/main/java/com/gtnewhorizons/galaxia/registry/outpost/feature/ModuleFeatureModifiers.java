package com.gtnewhorizons.galaxia.registry.outpost.feature;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ModuleFeatureModifiers(int buildSpeedModifierPercent, int upkeepMultiplierPercent,
    int powerDrawMultiplierPercent, Map<PlanetaryFeatureKey, Integer> coveredTiles,
    List<FeatureContribution> contributions) {

    public static final ModuleFeatureModifiers EMPTY = new ModuleFeatureModifiers(0, 100, 100, Map.of(), List.of());

    public ModuleFeatureModifiers {
        coveredTiles = coveredTiles.isEmpty() ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(coveredTiles));
        contributions = contributions.isEmpty() ? List.of() : List.copyOf(contributions);
    }

    public int coveredTiles(PlanetaryFeatureKey key) {
        return coveredTiles.getOrDefault(key, 0);
    }
}
