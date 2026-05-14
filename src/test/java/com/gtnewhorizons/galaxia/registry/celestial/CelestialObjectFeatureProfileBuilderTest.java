package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;

final class CelestialObjectFeatureProfileBuilderTest {

    @Test
    void builderCanDefineFeatureProfileWithoutNestedLambda() {
        CelestialObject object = CelestialObject.builder()
            .id(CelestialObjectId.EGORA)
            .featureTileChance(0.18)
            .feature(PlanetaryFeatureRegistry.REGOLITH_FLATS, 3.0)
            .feature(PlanetaryFeatureRegistry.MINERAL_VEIN, 1.5)
            .build();

        assertEquals(
            0.18,
            object.featureProfile()
                .featureTileChance());
        assertEquals(
            3.0,
            object.featureProfile()
                .weights()
                .get(PlanetaryFeatureRegistry.REGOLITH_FLATS.key()));
        assertEquals(
            1.5,
            object.featureProfile()
                .weights()
                .get(PlanetaryFeatureRegistry.MINERAL_VEIN.key()));
    }
}
