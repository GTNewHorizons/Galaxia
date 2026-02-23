package com.gtnewhorizons.galaxia.client.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizons.galaxia.core.Galaxia;

@Config(modid = Galaxia.MODID, category = "Overlay")
@Config.LangKey("galaxia.config.category.overlay")
public class GalaxiaConfigOverlay {

    /**
     * HUD global offsets.
     */
    @Config.LangKey("galaxia.config.overlay.horizontal_offset")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = -200, max = 200)
    public static int hudOffsetX;

    @Config.LangKey("galaxia.config.overlay.vertical_offset")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = -200, max = 200)
    public static int hudOffsetY;

    /**
     * Bar orientation.
     */

    @Config.LangKey("galaxia.config.overlay.bars_orientation")
    @Config.DefaultEnum("HORIZONTAL")
    public static BarOrientation barOrientation;

    /**
     * Visibility and per-bar offsets.
     */
    @Config.LangKey("galaxia.config.overlay.show_oxygen_bar")
    @Config.DefaultBoolean(true)
    public static boolean showOxygenBar;

    @Config.LangKey("galaxia.config.overlay.show_temperature_bar")
    @Config.DefaultBoolean(true)
    public static boolean showTemperatureBar;

    @Config.LangKey("galaxia.config.overlay.oxygen_bar_horizontal_offset")
    @Config.DefaultInt(87)
    @Config.RangeInt(min = -300, max = 300)
    public static int oxygenOffsetX;

    @Config.LangKey("galaxia.config.overlay.oxygen_bar_vertical_offset")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = -300, max = 300)
    public static int oxygenOffsetY;

    @Config.LangKey("galaxia.config.overlay.temperature_bar_horizontal_offset")
    @Config.DefaultInt(-87)
    @Config.RangeInt(min = -300, max = 300)
    public static int temperatureOffsetX;

    @Config.LangKey("galaxia.config.overlay.temperature_bar_vertical_offset")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = -300, max = 300)
    public static int temperatureOffsetY;

    /**
     * Critical thresholds (logic layer, not rendering).
     */

    @Config.LangKey("galaxia.config.overlay.oxygen_bar_critical")
    @Config.DefaultDouble(0.25D)
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public static double lowOxygenThreshold;

    @Config.LangKey("galaxia.config.overlay.temperature_bar_too_cold")
    @Config.DefaultDouble(0.35D)
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public static double temperatureLowThreshold;

    @Config.LangKey("galaxia.config.overlay.temperature_bar_too_hot")
    @Config.DefaultDouble(0.65D)
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public static double temperatureHighThreshold;

    /**
     * Pulse / warning visual settings.
     */

    @Config.LangKey("galaxia.config.overlay.pulse.speed")
    @Config.DefaultDouble(150.0D)
    @Config.RangeDouble(min = 50.0D, max = 500.0D)
    public static double pulseSpeed;

    @Config.LangKey("galaxia.config.overlay.pulse.amplitude")
    @Config.DefaultDouble(0.3D)
    @Config.RangeDouble(min = 0.0D, max = 0.5D)
    public static double pulseAmplitude;

    /**
     * Texture pixel dimensions.
     *
     * Set to the actual pixel size of your texture to make correct UV mapping.
     * Defaults match bar size so default behavior stays compatible.
     */
    @Config.LangKey("galaxia.config.overlay.texture.oxygen_bar_width")
    @Config.DefaultInt(81)
    @Config.RangeInt(min = 1, max = 2048)
    public static int oxygenTextureWidth;

    @Config.LangKey("galaxia.config.overlay.texture.oxygen_bar_height")
    @Config.DefaultInt(9)
    @Config.RangeInt(min = 1, max = 2048)
    public static int oxygenTextureHeight;

    @Config.LangKey("galaxia.config.overlay.texture.temperature_bar_width")
    @Config.DefaultInt(81)
    @Config.RangeInt(min = 1, max = 2048)
    public static int temperatureTextureWidth;

    @Config.LangKey("galaxia.config.overlay.texture.temperature_bar_height")
    @Config.DefaultInt(9)
    @Config.RangeInt(min = 1, max = 2048)
    public static int temperatureTextureHeight;

    public enum BarOrientation {
        VERTICAL,
        HORIZONTAL
    }
}
