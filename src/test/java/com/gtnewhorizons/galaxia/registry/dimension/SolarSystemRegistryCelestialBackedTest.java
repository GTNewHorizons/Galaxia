package com.gtnewhorizons.galaxia.registry.dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderSpace;
import com.gtnewhorizons.galaxia.registry.rocketmodules.utility.EnumTiers;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class SolarSystemRegistryCelestialBackedTest {

    @Test
    void getByIdMaterializesDefinitionFromCelestialRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();

        DimensionDef def = SolarSystemRegistry.getById(DimensionEnum.MARS.getId());

        assertNotNull(def);
        assertEquals(DimensionEnum.MARS.getName(), def.name());
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
}
