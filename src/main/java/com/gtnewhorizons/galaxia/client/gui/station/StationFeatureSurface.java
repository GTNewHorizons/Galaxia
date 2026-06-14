package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;

final class StationFeatureSurface {

    List<PlanetaryFeatureDefinition> hoverDefinitions(AutomatedFacility facility,
        StationMapViewport.TilePosition hoverTile, List<PlanetaryFeatureDefinition> output) {
        output.clear();
        if (facility == null || hoverTile == null) return output;
        appendDefinitions(facility.planetaryFeaturesAt(hoverTile.dx(), hoverTile.dy()), output);
        return output;
    }

    static List<PlanetaryFeatureDefinition> definitionsFor(Iterable<PlanetaryFeatureKey> features) {
        if (features == null) return Collections.emptyList();
        List<PlanetaryFeatureDefinition> definitions = new ArrayList<>();
        appendDefinitions(features, definitions);
        return definitions;
    }

    private static void appendDefinitions(Iterable<PlanetaryFeatureKey> features,
        List<PlanetaryFeatureDefinition> output) {
        if (features == null) return;
        for (PlanetaryFeatureKey key : features) {
            if (key == null) continue;
            PlanetaryFeatureDefinition definition = PlanetaryFeatureRegistry.get(key);
            if (definition != null) output.add(definition);
        }
    }
}
