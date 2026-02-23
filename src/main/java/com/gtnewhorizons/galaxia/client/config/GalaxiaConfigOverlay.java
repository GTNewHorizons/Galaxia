package com.gtnewhorizons.galaxia.client.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizons.galaxia.core.Galaxia;

@Config(modid = Galaxia.MODID, category = "Overlay")
public class GalaxiaConfigOverlay {

    /**
     * All settings for the oxygen and temperature HUD overlay.
     */

    @Config.Comment("Choose where the bars appear on screen")
    @Config.DefaultEnum("ABOVE_HOTBAR")
    public static HudPreset hudPreset;

    @Config.Comment("Horizontal offset in pixels (positive = right, negative = left)")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = -200, max = 200)
    public static int hudOffsetX;

    @Config.Comment("Vertical offset in pixels (positive = down, negative = up)")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = -200, max = 200)
    public static int hudOffsetY;

    @Config.Comment("Show the Oxygen bar")
    @Config.DefaultBoolean(true)
    public static boolean showOxygenBar;

    @Config.Comment("Show the Temperature bar")
    @Config.DefaultBoolean(true)
    public static boolean showTemperatureBar;

    /**
     * HUD position presets.
     */
    public enum HudPreset {
        ABOVE_HOTBAR, // next to the hotbar (default)
        CLASSIC_GC // classic Galacticraft style (right side of screen)
    }
}
