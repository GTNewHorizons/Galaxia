package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class GalaxiaAtmosphereFluidsTest {

    @BeforeAll
    static void setupMinecraft() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    @Test
    void registersPlaceholderAtmosphereFluidWithForge() {
        GalaxiaAtmosphereFluids.init();

        assertNotNull(FluidRegistry.getFluid(GalaxiaAtmosphereFluids.OXYGEN_NITROGEN_ATMOSPHERE_NAME));
    }
}
