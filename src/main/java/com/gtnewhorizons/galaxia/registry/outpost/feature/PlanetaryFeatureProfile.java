package com.gtnewhorizons.galaxia.registry.outpost.feature;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class PlanetaryFeatureProfile {

    public static final PlanetaryFeatureProfile NONE = builder().build();

    private final double featureTileChance;
    private final Map<PlanetaryFeatureKey, Double> weights;
    private final PlanetaryFeatureKey[] keys;
    private final double[] cumulativeWeights;
    private final double totalWeight;

    private PlanetaryFeatureProfile(double featureTileChance, Map<PlanetaryFeatureKey, Double> weights) {
        this.featureTileChance = clampChance(featureTileChance);
        this.weights = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(weights));
        this.keys = new PlanetaryFeatureKey[weights.size()];
        this.cumulativeWeights = new double[weights.size()];
        double total = 0.0;
        int index = 0;
        for (Map.Entry<PlanetaryFeatureKey, Double> entry : weights.entrySet()) {
            double weight = entry.getValue();
            total += weight;
            keys[index] = entry.getKey();
            cumulativeWeights[index] = total;
            index++;
        }
        this.totalWeight = total;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder().featureTileChance(featureTileChance)
            .weights(weights);
    }

    public double featureTileChance() {
        return featureTileChance;
    }

    public Map<PlanetaryFeatureKey, Double> weights() {
        return weights;
    }

    public double totalWeight() {
        return totalWeight;
    }

    public boolean canGenerateFeatures() {
        return featureTileChance > 0.0 && totalWeight > 0.0;
    }

    PlanetaryFeatureKey select(double roll) {
        if (totalWeight <= 0.0) return null;
        double target = roll * totalWeight;
        int low = 0;
        int high = keys.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (target < cumulativeWeights[mid]) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return low < keys.length ? keys[low] : keys[keys.length - 1];
    }

    private static double clampChance(double value) {
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static final class Builder {

        private double featureTileChance;
        private final Map<PlanetaryFeatureKey, Double> weights = new LinkedHashMap<>();

        public Builder featureTileChance(double featureTileChance) {
            this.featureTileChance = featureTileChance;
            return this;
        }

        public Builder weight(PlanetaryFeatureKey key, double weight) {
            Objects.requireNonNull(key, "Planetary feature key must not be null");
            if (weight > 0.0) {
                weights.put(key, weight);
            } else {
                weights.remove(key);
            }
            return this;
        }

        public Builder weight(PlanetaryFeatureDefinition definition, double weight) {
            Objects.requireNonNull(definition, "Planetary feature definition must not be null");
            return weight(definition.key(), weight);
        }

        public Builder weights(Map<PlanetaryFeatureKey, Double> weights) {
            this.weights.clear();
            if (weights != null) {
                for (Map.Entry<PlanetaryFeatureKey, Double> entry : weights.entrySet()) {
                    weight(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        public PlanetaryFeatureProfile build() {
            return new PlanetaryFeatureProfile(featureTileChance, weights);
        }
    }
}
