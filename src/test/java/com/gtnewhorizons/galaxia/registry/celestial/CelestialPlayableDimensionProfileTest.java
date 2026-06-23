package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.dimension.CelestialDimensionMaterializer;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.PlayableDimensionProfile;
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
                DimensionEnum.MARS,
                DimensionEnum.MOON,
                DimensionEnum.FROZEN_BELT,
                DimensionEnum.OVERWORLD,
                DimensionEnum.OVERWORLD_ORBIT),
            playableDimensions);

    }

    @Test
    void playableDimensionProfileRequiresWorldGeneration() {
        assertThrows(
            IllegalStateException.class,
            () -> PlayableDimensionProfile.builder(DimensionEnum.MARS)
                .build());
    }

    @Test
    void playableDimensionBodyFactsAreAuthoredOnCelestialProperties() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();

        CelestialObject mars = CelestialRegistry.findByDimension(DimensionEnum.MARS)
            .orElseThrow();

        DimensionDef def = CelestialDimensionMaterializer.materializeDefinition(mars);

        assertEquals(
            mars.properties()
                .localGravityG(),
            def.gravity());
        assertEquals(
            mars.properties()
                .massEarthRelative(),
            def.mass());
        assertEquals(
            mars.properties()
                .orbitalRadiusEarthRelative(),
            def.orbitalRadius());
        assertEquals(
            mars.properties()
                .radiusEarthRelative(),
            def.radius());
    }

    @Test
    void playableDimensionPressureIsAuthoredOnCelestialProperties() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();

        for (CelestialObject body : CelestialRegistry.getPlayableBodies()) {
            DimensionDef def = CelestialDimensionMaterializer.materializeDefinition(body);

            assertEquals(
                def.effects()
                    .getPressure(null),
                body.properties()
                    .surfacePressurePa());
        }
    }

    @Test
    void materializerCreatesDimensionDefForPlayableBody() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();

        CelestialObject mars = CelestialRegistry.findByDimension(DimensionEnum.MARS)
            .orElseThrow();

        DimensionDef def = CelestialDimensionMaterializer.materializeDefinition(mars);

        assertEquals(DimensionEnum.MARS.getName(), def.name());
        assertEquals(DimensionEnum.MARS.getId(), def.id());
        assertSame(
            mars.playableDimensionProfile()
                .provider(),
            def.provider());
        assertEquals(
            mars.properties()
                .localGravityG(),
            def.gravity());
        assertEquals(
            mars.playableDimensionProfile()
                .airResistance(),
            def.airResistance());
        assertEquals(
            mars.playableDimensionProfile()
                .tier(),
            def.tier());
        assertEquals(
            mars.playableDimensionProfile()
                .effects()
                .getTemperature(null),
            def.effects()
                .getTemperature(null));
        assertEquals(
            mars.playableDimensionProfile()
                .effects()
                .getOxygenPercent(null),
            def.effects()
                .getOxygenPercent(null));
        assertEquals(
            mars.playableDimensionProfile()
                .effects()
                .getPressure(null),
            def.effects()
                .getPressure(null));
    }

}
