package com.gtnewhorizons.galaxia.testing;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraft.init.Items;
import net.minecraft.item.Item;

import org.junit.jupiter.api.Test;

final class GalaxiaTestBootstrapPrematureItemsTest {

    @Test
    void minecraftBootstrapRepairsItemsWhenItemsClassLoadedBeforeRegistry() {
        Item prematureDiamond = Items.diamond;

        GalaxiaTestBootstrap.ensureMinecraft();

        assertNotNull(Items.diamond);
    }
}
