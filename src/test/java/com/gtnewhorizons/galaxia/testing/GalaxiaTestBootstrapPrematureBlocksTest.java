package com.gtnewhorizons.galaxia.testing;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import org.junit.jupiter.api.Test;

final class GalaxiaTestBootstrapPrematureBlocksTest {

    @Test
    void minecraftBootstrapRepairsBlocksWhenBlocksClassLoadedBeforeRegistry() {
        Block prematureFire = Blocks.fire;

        GalaxiaTestBootstrap.ensureMinecraft();

        assertNotNull(Blocks.fire);
    }
}
