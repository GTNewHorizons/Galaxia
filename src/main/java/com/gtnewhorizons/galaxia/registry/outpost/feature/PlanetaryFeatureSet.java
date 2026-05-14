package com.gtnewhorizons.galaxia.registry.outpost.feature;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class PlanetaryFeatureSet {

    private static final PlanetaryFeatureSet EMPTY = new PlanetaryFeatureSet(
        new EnumMap<>(PlanetaryFeatureLayer.class));

    private final Map<PlanetaryFeatureLayer, PlanetaryFeatureKey> features;

    private PlanetaryFeatureSet(EnumMap<PlanetaryFeatureLayer, PlanetaryFeatureKey> features) {
        this.features = Collections.unmodifiableMap(new EnumMap<>(features));
    }

    public static PlanetaryFeatureSet empty() {
        return EMPTY;
    }

    static PlanetaryFeatureSet of(EnumMap<PlanetaryFeatureLayer, PlanetaryFeatureKey> features) {
        return features.isEmpty() ? EMPTY : new PlanetaryFeatureSet(features);
    }

    public boolean isEmpty() {
        return features.isEmpty();
    }

    public PlanetaryFeatureKey get(PlanetaryFeatureLayer layer) {
        return features.get(layer);
    }

    public boolean contains(PlanetaryFeatureKey key) {
        return features.containsValue(key);
    }

    public PlanetaryFeatureKey primary() {
        PlanetaryFeatureKey resource = features.get(PlanetaryFeatureLayer.RESOURCE);
        if (resource != null) return resource;
        PlanetaryFeatureKey environment = features.get(PlanetaryFeatureLayer.ENVIRONMENT);
        if (environment != null) return environment;
        return features.get(PlanetaryFeatureLayer.TERRAIN);
    }

    public Collection<PlanetaryFeatureKey> values() {
        return features.values();
    }
}
