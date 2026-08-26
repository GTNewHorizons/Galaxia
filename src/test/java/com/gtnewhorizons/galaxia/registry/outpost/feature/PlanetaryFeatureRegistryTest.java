package com.gtnewhorizons.galaxia.registry.outpost.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

final class PlanetaryFeatureRegistryTest {

    @Test
    void definitionResolutionPreservesKnownKeyOrderAndSkipsUnknownKeys() {
        PlanetaryFeatureDefinition first = PlanetaryFeatureRegistry.REGOLITH_FLATS;
        PlanetaryFeatureDefinition second = PlanetaryFeatureRegistry.MINERAL_VEIN;
        PlanetaryFeatureKey missing = PlanetaryFeatureKey.of("planetary_feature_registry_missing");

        List<PlanetaryFeatureDefinition> definitions = PlanetaryFeatureRegistry
            .definitionsFor(Arrays.asList(first.key(), null, missing, second.key()));

        assertEquals(List.of(first, second), definitions);
    }
}
