package com.gtnewhorizons.galaxia.registry.outpost.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class PlanetaryFeatureGeneratorTest {

    @Test
    void emptyProfileGeneratesNoFeatures() {
        CelestialObject body = CelestialObject.builder()
            .id(CelestialObjectId.EGORA)
            .featureProfile(PlanetaryFeatureProfile.NONE)
            .build();

        assertNull(PlanetaryFeatureGenerator.featureAt(123L, StationTileCoord.of(1, 2), body));
        assertTrue(
            PlanetaryFeatureGenerator.featuresAt(123L, StationTileCoord.of(1, 2), body)
                .isEmpty());
    }

    @Test
    void singleWeightedFeatureIsDeterministic() {
        CelestialObject body = CelestialObject.builder()
            .id(CelestialObjectId.EGORA)
            .featureProfile(
                p -> p.featureTileChance(1.0)
                    .weight(PlanetaryFeatureRegistry.MINERAL_VEIN, 1.0))
            .build();

        StationTileCoord tile = findTileWith(987654321L, body, PlanetaryFeatureRegistry.MINERAL_VEIN.key());

        assertEquals(
            PlanetaryFeatureRegistry.MINERAL_VEIN.key(),
            PlanetaryFeatureGenerator.featureAt(987654321L, tile, body));
        assertTrue(
            PlanetaryFeatureGenerator.featuresAt(987654321L, tile, body)
                .contains(PlanetaryFeatureRegistry.MINERAL_VEIN.key()));
        assertEquals(
            PlanetaryFeatureGenerator.featureAt(987654321L, tile, body),
            PlanetaryFeatureGenerator.featureAt(987654321L, tile, body));
    }

    @Test
    void intCoordinateGenerationMatchesStationTileCoordinateGeneration() {
        CelestialObject body = CelestialObject.builder()
            .id(CelestialObjectId.EGORA)
            .featureProfile(
                p -> p.featureTileChance(1.0)
                    .weight(PlanetaryFeatureRegistry.MINERAL_VEIN, 1.0))
            .build();
        StationTileCoord tile = findTileWith(987654321L, body, PlanetaryFeatureRegistry.MINERAL_VEIN.key());

        assertEquals(
            PlanetaryFeatureGenerator.featureAt(987654321L, tile, body),
            PlanetaryFeatureGenerator.featureAt(987654321L, tile.dx(), tile.dy(), body));
    }

    @Test
    void differentStationSaltsCanProduceDifferentFeatureLayouts() {
        CelestialObject body = CelestialObject.builder()
            .id(CelestialObjectId.EGORA)
            .featureProfile(
                p -> p.featureTileChance(0.5)
                    .weight(PlanetaryFeatureRegistry.MINERAL_VEIN, 1.0))
            .build();

        int differences = 0;
        for (int i = -8; i <= 8; i++) {
            StationTileCoord tile = StationTileCoord.of(i, 0);
            if (!java.util.Objects.equals(
                PlanetaryFeatureGenerator.featureAt(1L, tile, body),
                PlanetaryFeatureGenerator.featureAt(2L, tile, body))) {
                differences++;
            }
        }

        assertNotEquals(0, differences);
    }

    @Test
    void terrainAndResourceFeaturesCanCoexistOnOneTile() {
        CelestialObject body = CelestialObject.builder()
            .id(CelestialObjectId.EGORA)
            .featureProfile(
                p -> p.featureTileChance(1.0)
                    .weight(PlanetaryFeatureRegistry.STABLE_BEDROCK, 1.0)
                    .weight(PlanetaryFeatureRegistry.MINERAL_VEIN, 1.0))
            .build();

        boolean foundCoexistence = false;
        for (int x = StationTileCoord.MIN; x <= StationTileCoord.MAX && !foundCoexistence; x++) {
            for (int y = StationTileCoord.MIN; y <= StationTileCoord.MAX && !foundCoexistence; y++) {
                java.util.List<PlanetaryFeatureKey> features = PlanetaryFeatureGenerator
                    .featuresAt(42L, StationTileCoord.of(x, y), body);
                foundCoexistence = features.contains(PlanetaryFeatureRegistry.STABLE_BEDROCK.key())
                    && features.contains(PlanetaryFeatureRegistry.MINERAL_VEIN.key());
            }
        }

        assertTrue(foundCoexistence);
    }

    @Test
    void defaultFeatureRegistrationProvidesUsableDefinitions() {
        assertFalse(
            PlanetaryFeatureRegistry.all()
                .isEmpty());
        for (PlanetaryFeatureDefinition definition : PlanetaryFeatureRegistry.all()) {
            assertNotNull(definition.key());
            assertFalse(
                definition.displayName()
                    .isBlank());
            assertNotNull(definition.texture());
            assertNotNull(definition.layer());
            assertNotNull(definition.placement());
        }
    }

    @Test
    void isolatedMagmaPoolsRejectNearbySecondPool() {
        CelestialObject body = CelestialObject.builder()
            .id(CelestialObjectId.EGORA)
            .featureProfile(
                p -> p.featureTileChance(1.0)
                    .weight(PlanetaryFeatureRegistry.MAGMA_POOL, 1.0))
            .build();

        StationTileCoord vent = findTileWith(99887766L, body, PlanetaryFeatureRegistry.MAGMA_POOL.key());

        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -6; dy <= 6; dy++) {
                if (Math.abs(dx) + Math.abs(dy) > 6 || dx == 0 && dy == 0) continue;
                int x = vent.dx() + dx;
                int y = vent.dy() + dy;
                if (x < StationTileCoord.MIN || x > StationTileCoord.MAX) continue;
                if (y < StationTileCoord.MIN || y > StationTileCoord.MAX) continue;
                assertFalse(
                    PlanetaryFeatureGenerator.featuresAt(99887766L, StationTileCoord.of(x, y), body)
                        .contains(PlanetaryFeatureRegistry.MAGMA_POOL.key()),
                    "Magma pool generated too close to " + vent);
            }
        }
    }

    private static StationTileCoord findTileWith(long salt, CelestialObject body, PlanetaryFeatureKey key) {
        for (int x = StationTileCoord.MIN; x <= StationTileCoord.MAX; x++) {
            for (int y = StationTileCoord.MIN; y <= StationTileCoord.MAX; y++) {
                StationTileCoord tile = StationTileCoord.of(x, y);
                if (PlanetaryFeatureGenerator.featuresAt(salt, tile, body)
                    .contains(key)) {
                    return tile;
                }
            }
        }
        throw new AssertionError("No generated tile found for " + key);
    }
}
