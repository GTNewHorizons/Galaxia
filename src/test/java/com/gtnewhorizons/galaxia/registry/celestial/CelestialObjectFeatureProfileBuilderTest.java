package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

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

    @Test
    void panspiraHasAllPlanetaryFeaturesForTestingWithRareHazards() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();

        CelestialObject panspira = CelestialRegistry.findByDimension(DimensionEnum.PANSPIRA)
            .orElseThrow();
        Map<PlanetaryFeatureKey, Double> weights = panspira.featureProfile()
            .weights();

        assertEquals(
            PlanetaryFeatureRegistry.all()
                .stream()
                .map(definition -> definition.key())
                .collect(Collectors.toSet()),
            weights.keySet());
        assertEquals(0.5, weights.get(PlanetaryFeatureRegistry.VOLATILE_DEPOSIT.key()));
        assertEquals(0.4, weights.get(PlanetaryFeatureRegistry.MAGMA_POOL.key()));
    }
}
