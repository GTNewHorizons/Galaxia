package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;

class StationFeatureSurfaceTest {

    @Test
    void definitionLookupSkipsNullAndUnknownFeatureKeys() {
        PlanetaryFeatureDefinition first = PlanetaryFeatureRegistry.REGOLITH_FLATS;
        PlanetaryFeatureDefinition second = PlanetaryFeatureRegistry.MINERAL_VEIN;
        PlanetaryFeatureKey missing = PlanetaryFeatureKey.of("station_feature_surface_missing");

        List<PlanetaryFeatureDefinition> definitions = new ArrayList<>();
        StationFeatureSurface.appendDefinitions(Arrays.asList(first.key(), null, missing, second.key()), definitions);

        assertEquals(List.of(first, second), definitions);
    }
}
