package com.gtnewhorizons.galaxia.testing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;

final class GalaxiaTestBootstrapTest {

    @Test
    void minecraftBootstrapExposesCommonVanillaAndForgeRegistries() {
        GalaxiaTestBootstrap.ensureMinecraft();

        assertNotNull(Blocks.fire);
        assertNotNull(Items.diamond);
        assertNotNull(FluidRegistry.WATER);
        assertNotNull(FluidRegistry.LAVA);
    }

    @Test
    void celestialBootstrapBuildsRegistryHierarchy() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();

        assertFalse(
            CelestialRegistry.getRoots()
                .isEmpty());
    }
}
