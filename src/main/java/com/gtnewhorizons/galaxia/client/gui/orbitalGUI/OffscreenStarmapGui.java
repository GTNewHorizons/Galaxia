package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.cleanroommc.modularui.screen.GuiScreenWrapper;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.sbancuz.offscreen.api.OffscreenAPI;
import com.sbancuz.offscreen.api.UIRegistry;
import com.sbancuz.offscreen.integration.mui2.Mui2UI;
import com.sbancuz.offscreen.window.Window;
import com.sbancuz.offscreen.window.input.MouseShadow;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Optional client-only map. Gameplay commands do not occupy the player's server container. */
@SideOnly(Side.CLIENT)
public final class OffscreenStarmapGui {

    private static Window window;

    static {
        // Navigation hands Offscreen a Minecraft wrapper, while its MUI adapter accepts ModularScreen.
        UIRegistry
            .register(GuiScreenWrapper.class, screen -> UIRegistry.resolve(((GuiScreenWrapper) screen).getScreen()));
        UIRegistry.register(
            GuiContainerWrapper.class,
            screen -> UIRegistry.resolve(((GuiContainerWrapper) screen).getScreen()));
    }

    private OffscreenStarmapGui() {}

    public static Window toggle() {
        if (OffscreenAPI.isOpen(window)) {
            OffscreenAPI.close(window);
            window = null;
            return null;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return null;
        window = OffscreenAPI.open(
            () -> new ModularScreen(
                Galaxia.MODID,
                GalacticMapWidget.build(mc.thePlayer, MouseShadow::getPixelWidth, MouseShadow::getPixelHeight)),
            screen -> {
                ModularScreen map = (ModularScreen) screen;
                map.getContext()
                    .setSettings(new UISettings());
                return new Mui2UI(map);
            });
        OffscreenAPI.setTitle(window, StatCollector.translateToLocal("galaxia.gui.orbital.window.title"));
        return window;
    }
}
