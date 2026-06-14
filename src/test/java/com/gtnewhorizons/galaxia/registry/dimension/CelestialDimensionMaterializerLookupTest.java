package com.gtnewhorizons.galaxia.registry.dimension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.Vec3;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderSpace;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialDimensionMaterializerLookupTest {

    @Test
    void findsPlayableDefinitionByDimensionId() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();

        DimensionDef def = CelestialDimensionMaterializer.findDefinitionById(DimensionEnum.MARS.getId())
            .orElseThrow();

        assertEquals(DimensionEnum.MARS.getName(), def.name());
        assertEquals(0.25, def.gravity());
    }

    @Test
    void ignoresDimensionIdsWithoutPlayableProfiles() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();

        assertTrue(
            CelestialDimensionMaterializer.findDefinitionById(DimensionEnum.TENEBRAE.getId())
                .isEmpty());
    }

    @Test
    void registerPlayableDimensionsIsIdempotent() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();

        assertDoesNotThrow(CelestialDimensionMaterializer::registerPlayableDimensions);
        assertDoesNotThrow(CelestialDimensionMaterializer::registerPlayableDimensions);
    }

    @Test
    void registeredWorldProviderUsesCelestialWorldGenerationProfiles() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        CelestialDimensionMaterializer.registerPlayableDimensions();

        WorldProviderSpace mars = configuredProvider(DimensionEnum.MARS);
        assertEquals(DimensionEnum.MARS.getName(), mars.getDimensionName());
        assertVec3(0.15, 0.1, 0.3, mars.getFogColor(0, 0));

        WorldProviderSpace moon = configuredProvider(DimensionEnum.MOON);
        assertEquals(DimensionEnum.MOON.getName(), moon.getDimensionName());
        assertEquals(80, moon.getAverageGroundLevel());
        assertVec3(0, 0, 0.001, moon.getSkyColor(null, 0));

        WorldProviderSpace panspira = configuredProvider(DimensionEnum.PANSPIRA);
        assertEquals(DimensionEnum.PANSPIRA.getName(), panspira.getDimensionName());
        assertEquals(50, panspira.getAverageGroundLevel());
        assertVec3(0.15, 0.1, 0.3, panspira.getFogColor(0, 0));

        WorldProviderSpace overworld = configuredProvider(DimensionEnum.OVERWORLD);
        assertEquals(DimensionEnum.OVERWORLD.getName(), overworld.getDimensionName());
    }

    @Test
    void worldProviderRejectsUnregisteredDimensionConfiguration() {
        WorldProviderSpace provider = new WorldProviderSpace();

        assertThrows(IllegalStateException.class, () -> provider.setDimension(DimensionEnum.TENEBRAE.getId()));
    }

    private static WorldProviderSpace configuredProvider(DimensionEnum dimension) {
        WorldProviderSpace provider = new WorldProviderSpace();
        provider.setDimension(dimension.getId());
        return provider;
    }

    private static void assertVec3(double x, double y, double z, Vec3 actual) {
        assertEquals(x, actual.xCoord, 0.0001);
        assertEquals(y, actual.yCoord, 0.0001);
        assertEquals(z, actual.zCoord, 0.0001);
    }
}
