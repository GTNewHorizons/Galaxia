package com.gtnewhorizons.galaxia.registry.outpost.feature;

import java.util.EnumMap;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class PlanetaryFeatureGenerator {

    private PlanetaryFeatureGenerator() {}

    public static PlanetaryFeatureKey featureAt(long stationFeatureSalt, StationTileCoord tile, CelestialObject body) {
        return featuresAt(stationFeatureSalt, tile, body).primary();
    }

    public static PlanetaryFeatureSet featuresAt(long stationFeatureSalt, StationTileCoord tile, CelestialObject body) {
        if (tile == null || body == null) return PlanetaryFeatureSet.empty();
        PlanetaryFeatureProfile profile = body.featureProfile();
        if (profile == null || !profile.canGenerateFeatures()) return PlanetaryFeatureSet.empty();
        long base = mix(
            stationFeatureSalt ^ body.id()
                .ordinal());
        EnumMap<PlanetaryFeatureLayer, PlanetaryFeatureKey> selected = new EnumMap<>(PlanetaryFeatureLayer.class);
        EnumMap<PlanetaryFeatureLayer, Double> selectedScores = new EnumMap<>(PlanetaryFeatureLayer.class);
        for (Map.Entry<PlanetaryFeatureKey, Double> entry : profile.weights()
            .entrySet()) {
            PlanetaryFeatureDefinition definition = PlanetaryFeatureRegistry.get(entry.getKey());
            if (definition == null) continue;
            double weightShare = entry.getValue() / profile.totalWeight();
            PlanetaryFeaturePlacement placement = definition.placement();
            if (!placement.contains(base, definition.key(), tile, profile.featureTileChance(), weightShare)) continue;
            PlanetaryFeatureLayer layer = definition.layer();
            double score = placement.score(base, definition.key(), tile);
            Double previousScore = selectedScores.get(layer);
            if (previousScore == null || score > previousScore) {
                selected.put(layer, definition.key());
                selectedScores.put(layer, score);
            }
        }
        return PlanetaryFeatureSet.of(selected);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static double unitDouble(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }
}
