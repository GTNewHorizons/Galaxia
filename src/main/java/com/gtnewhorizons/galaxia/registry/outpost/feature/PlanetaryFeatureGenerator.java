package com.gtnewhorizons.galaxia.registry.outpost.feature;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class PlanetaryFeatureGenerator {

    private static final List<PlanetaryFeatureKey> NO_FEATURES = List.of();

    private PlanetaryFeatureGenerator() {}

    public static PlanetaryFeatureKey featureAt(long stationFeatureSalt, StationTileCoord tile, CelestialObject body) {
        return firstFeature(featuresAt(stationFeatureSalt, tile, body));
    }

    public static PlanetaryFeatureKey featureAt(long stationFeatureSalt, int dx, int dy, CelestialObject body) {
        return firstFeature(featuresAt(stationFeatureSalt, dx, dy, body));
    }

    public static List<PlanetaryFeatureKey> featuresAt(long stationFeatureSalt, StationTileCoord tile,
        CelestialObject body) {
        if (tile == null || body == null) return NO_FEATURES;
        return featuresAt(stationFeatureSalt, tile.dx(), tile.dy(), body);
    }

    public static List<PlanetaryFeatureKey> featuresAt(long stationFeatureSalt, int dx, int dy, CelestialObject body) {
        if (body == null) return NO_FEATURES;
        PlanetaryFeatureProfile profile = body.featureProfile();
        if (profile == null || !profile.canGenerateFeatures()) return NO_FEATURES;
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
            if (!placement.contains(base, definition.key(), dx, dy, profile.featureTileChance(), weightShare)) continue;
            PlanetaryFeatureLayer layer = definition.layer();
            double score = placement.score(base, definition.key(), dx, dy);
            Double previousScore = selectedScores.get(layer);
            if (previousScore == null || score > previousScore) {
                selected.put(layer, definition.key());
                selectedScores.put(layer, score);
            }
        }
        return orderedFeatures(selected);
    }

    private static PlanetaryFeatureKey firstFeature(List<PlanetaryFeatureKey> features) {
        return features.isEmpty() ? null : features.get(0);
    }

    private static List<PlanetaryFeatureKey> orderedFeatures(EnumMap<PlanetaryFeatureLayer, PlanetaryFeatureKey> map) {
        if (map.isEmpty()) return NO_FEATURES;
        List<PlanetaryFeatureKey> features = new java.util.ArrayList<>(map.size());
        addIfPresent(features, map, PlanetaryFeatureLayer.RESOURCE);
        addIfPresent(features, map, PlanetaryFeatureLayer.ENVIRONMENT);
        addIfPresent(features, map, PlanetaryFeatureLayer.TERRAIN);
        return List.copyOf(features);
    }

    private static void addIfPresent(List<PlanetaryFeatureKey> features,
        EnumMap<PlanetaryFeatureLayer, PlanetaryFeatureKey> map, PlanetaryFeatureLayer layer) {
        PlanetaryFeatureKey feature = map.get(layer);
        if (feature != null) features.add(feature);
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
