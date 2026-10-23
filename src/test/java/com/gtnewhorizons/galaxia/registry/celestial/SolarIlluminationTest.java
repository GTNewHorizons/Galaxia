package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SolarIlluminationTest {

    /** Product contract: the same source is stronger outside the atmosphere, with one daylight reference. */
    @Test
    void vacuumAndSurfaceShareOverworldDaylightReference() {
        assertEquals(1.0, SolarIllumination.intensity(1, 1, 101325, 1, 1, 1), 1e-9);
        assertEquals(2.0, SolarIllumination.intensity(1, 1, 0, 1, 1, 1), 1e-9);
    }

    @Test
    void authoredOverworldAtmosphereMatchesTheSolarReference() {
        com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap.ensureCelestialRegistry();
        double pressure = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow()
            .properties()
            .surfacePressurePa();
        assertEquals(101325, pressure);
        double surface = SolarIllumination.intensity(1, 1, pressure, 1, 1, 1);
        assertEquals(2, SolarIllumination.intensity(1, 1, 0, 1, 1, 1) / surface, 1e-9);
    }

    @Test
    void distanceLightAndWeatherActOnTheSameReference() {
        assertEquals(0, SolarIllumination.intensity(1, 1, 101325, 0, 1, 1));
        assertEquals(0.5, SolarIllumination.intensity(2, 1, 0, 1, 1, 1), 1e-9);
        assertEquals(0.25, SolarIllumination.intensity(1, 1, 101325, 1, 0.5, 0.5), 1e-9);
        assertEquals(4, SolarIllumination.intensity(1, 2, 0, 1, 1, 1), 1e-9);
    }

    @Test
    void fixedOrbitalSunStaysOnTheCollectionSide() {
        var sun = new com.gtnewhorizons.galaxia.registry.dimension.sky.CelestialBodyBuilder()
            .texture("minecraft:textures/environment/sun.png")
            .fixedAngle(SolarIllumination.ORBITAL_SKY_ANGLE)
            .build();
        assertEquals(sun.orbitalAngleAt(0), sun.orbitalAngleAt(24000 * 365L));
        double angle = sun.orbitalAngleAt(0) * Math.PI * 2;
        assertEquals(SolarIllumination.ORBITAL_SUN_DIRECTION.offsetX, -Math.sin(angle), 1e-6);
        assertEquals(0, Math.cos(angle), 1e-6);
    }
}
