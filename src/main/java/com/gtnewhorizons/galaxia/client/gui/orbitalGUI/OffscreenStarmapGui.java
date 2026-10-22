package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.sbancuz.offscreen.integration.mui2.OffscreenSimpleFactory;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Server-synced galactic starmap shown in an Offscreen window.
 * <p>
 * Both sides register the same factory name and panel builder. Only the physical client loads the Offscreen factory,
 * whose window methods reference client-only Minecraft classes.
 */
public final class OffscreenStarmapGui {

    private OffscreenStarmapGui() {}

    private static final String FACTORY_NAME = "galaxia_offscreen_starmap";

    public static final SimpleGuiFactory FACTORY = FMLCommonHandler.instance()
        .getSide()
        .isClient() ? clientFactory() : new SimpleGuiFactory(FACTORY_NAME, OffscreenStarmapGui::build);

    @SideOnly(Side.CLIENT)
    private static SimpleGuiFactory clientFactory() {
        return new OffscreenSimpleFactory(FACTORY_NAME, Galaxia.MODID, OffscreenStarmapGui::build);
    }

    @SideOnly(Side.CLIENT)
    public static void toggle() {
        ((OffscreenSimpleFactory) FACTORY).toggleOffscreen();
    }

    private static ModularPanel build(final GuiData data, final PanelSyncManager syncManager,
        final UISettings settings) {
        return GalacticMapWidget.build(syncManager, data.getPlayer());
    }
}
