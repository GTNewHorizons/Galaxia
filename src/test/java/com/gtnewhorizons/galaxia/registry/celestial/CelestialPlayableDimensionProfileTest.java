package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.dimension.CelestialDimensionMaterializer;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.SpaceStation;
import com.gtnewhorizons.galaxia.registry.dimension.WorldGenerationAdapter;
import com.gtnewhorizons.galaxia.registry.dimension.asteroidbelts.FrozenBelt;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderSpace;
import com.gtnewhorizons.galaxia.registry.rocketmodules.utility.EnumTiers;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialPlayableDimensionProfileTest {

    @Test
    void registryExposesOnlyPlayableBodiesWithDimensionProfiles() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();

        Set<DimensionEnum> playableDimensions = CelestialRegistry.getPlayableBodies()
            .stream()
            .map(
                body -> body.playableDimensionProfile()
                    .dimension())
            .collect(Collectors.toSet());

        assertEquals(
            Set.of(
                DimensionEnum.PANSPIRA,
                DimensionEnum.MARS,
                DimensionEnum.MOON,
                DimensionEnum.FROZEN_BELT,
                DimensionEnum.OVERWORLD,
                DimensionEnum.OVERWORLD_ORBIT),
            playableDimensions);

        CelestialRegistry.getPlayableBodies()
            .forEach(
                body -> assertNotNull(
                    body.playableDimensionProfile()
                        .worldGenerationAdapter()));

        CelestialRegistry.getPlayableBodies()
            .forEach(
                body -> assertNotSame(
                    WorldGenerationAdapter.none(),
                    body.playableDimensionProfile()
                        .worldGenerationAdapter()));
    }

    @Test
    void materializerCreatesDimensionDefFromPlayableProfile() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();

        CelestialObject mars = CelestialRegistry.findByDimension(DimensionEnum.MARS)
            .orElseThrow();

        DimensionDef def = CelestialDimensionMaterializer.materializeDefinition(mars);

        assertEquals(DimensionEnum.MARS.getName(), def.name());
        assertEquals(DimensionEnum.MARS.getId(), def.id());
        assertSame(WorldProviderSpace.class, def.provider());
        assertEquals(0.25, def.gravity());
        assertEquals(0.1, def.airResistance());
        assertEquals(EnumTiers.TIER_2, def.tier());
        assertEquals(
            67,
            def.effects()
                .getTemperature(null));
        assertEquals(
            0,
            def.effects()
                .getOxygenPercent(null));
        assertEquals(
            1,
            def.effects()
                .getPressure(null));
    }

    @Test
    void materializerPreservesRuntimeContractForEveryPlayableBody() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();

        Set<ExpectedDimension> expectedDimensions = Set.of(
            new ExpectedDimension(
                DimensionEnum.PANSPIRA,
                WorldProviderSpace.class,
                2.25,
                1.0,
                3.0,
                0.6 * 23481,
                1.5,
                EnumTiers.TIER_1,
                423,
                0,
                300),
            new ExpectedDimension(
                DimensionEnum.MARS,
                WorldProviderSpace.class,
                0.25,
                0.1,
                0.25,
                1.52 * 23481,
                0.53,
                EnumTiers.TIER_2,
                67,
                0,
                1),
            new ExpectedDimension(
                DimensionEnum.MOON,
                WorldProviderSpace.class,
                0.25,
                0.01,
                0.012,
                23481,
                0.27,
                EnumTiers.TIER_1,
                225,
                0,
                0),
            new ExpectedDimension(
                DimensionEnum.FROZEN_BELT,
                FrozenBelt.WorldProviderFrozenBelt.class,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                EnumTiers.TIER_1,
                67,
                0,
                1),
            new ExpectedDimension(
                DimensionEnum.OVERWORLD,
                WorldProviderSpace.class,
                1.0,
                1.0,
                0.0,
                23481,
                0.0,
                EnumTiers.TIER_1,
                273,
                100,
                1),
            new ExpectedDimension(
                DimensionEnum.OVERWORLD_ORBIT,
                SpaceStation.WorldProviderSpaceStation.class,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                EnumTiers.TIER_1,
                67,
                0,
                1));

        Set<ExpectedDimension> actualDimensions = CelestialRegistry.getPlayableBodies()
            .stream()
            .map(CelestialDimensionMaterializer::materializeDefinition)
            .map(ExpectedDimension::from)
            .collect(Collectors.toSet());

        assertEquals(expectedDimensions, actualDimensions);
    }

    private record ExpectedDimension(DimensionEnum dimension, Class<?> provider, double gravity, double airResistance,
        double mass, double orbitalRadius, double radius, EnumTiers tier, int baseTemperature, int oxygenPercent,
        int pressure) {

        private static ExpectedDimension from(DimensionDef def) {
            return new ExpectedDimension(
                DimensionEnum.fromId(def.id()),
                def.provider(),
                def.gravity(),
                def.airResistance(),
                def.mass(),
                def.orbitalRadius(),
                def.radius(),
                def.tier(),
                def.effects()
                    .getTemperature(null),
                def.effects()
                    .getOxygenPercent(null),
                def.effects()
                    .getPressure(null));
        }
    }
}
