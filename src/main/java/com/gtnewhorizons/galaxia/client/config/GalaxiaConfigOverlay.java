package com.gtnewhorizons.galaxia.client.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizons.galaxia.core.Galaxia;

@Config(modid = Galaxia.MODID, category = "Overlay")
public class GalaxiaConfigOverlay {

    /**
     * HUD global offsets.
     */

    @Config.Comment("Global horizontal offset (positive = right)")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = -200, max = 200)
    public static int hudOffsetX;

    @Config.Comment("Global vertical offset (positive = down)")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = -200, max = 200)
    public static int hudOffsetY;

    /**
     * Bar orientation.
     */

    @Config.Comment("Orientation of bars")
    @Config.DefaultEnum("HORIZONTAL")
    public static BarOrientation barOrientation;

    /**
     * Visibility and per-bar offsets.
     */
    @Config.Comment("Show the Oxygen bar")
    @Config.DefaultBoolean(true)
    public static boolean showOxygenBar;

    @Config.Comment("Show the Temperature bar")
    @Config.DefaultBoolean(true)
    public static boolean showTemperatureBar;

    @Config.Comment("Additional horizontal offset for Oxygen bar (pixels)")
    @Config.DefaultInt(87)
    @Config.RangeInt(min = -300, max = 300)
    public static int oxygenOffsetX;

    @Config.Comment("Additional vertical offset for Oxygen bar (pixels)")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = -300, max = 300)
    public static int oxygenOffsetY;

    @Config.Comment("Additional horizontal offset for Temperature bar (pixels)")
    @Config.DefaultInt(-87)
    @Config.RangeInt(min = -300, max = 300)
    public static int temperatureOffsetX;

    @Config.Comment("Additional vertical offset for Temperature bar (pixels)")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = -300, max = 300)
    public static int temperatureOffsetY;

    /**
     * Critical thresholds (logic layer, not rendering).
     */

    @Config.Comment("Oxygen level below which the bar is considered critical (0.0 - 1.0)")
    @Config.DefaultDouble(0.25D)
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public static double lowOxygenThreshold;

    @Config.Comment("Temperature below which it is considered too cold (0.0 - 1.0)")
    @Config.DefaultDouble(0.35D)
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public static double temperatureLowThreshold;

    @Config.Comment("Temperature above which it is considered too hot (0.0 - 1.0)")
    @Config.DefaultDouble(0.65D)
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public static double temperatureHighThreshold;

    /**
     * Pulse / warning visual settings.
     */

    @Config.Comment("Pulse speed (higher = slower). sin(currentTimeMillis / pulseSpeed)")
    @Config.DefaultDouble(150.0D)
    @Config.RangeDouble(min = 50.0D, max = 500.0D)
    public static double pulseSpeed;

    @Config.Comment("Pulse amplitude (0.0-0.5) – how strongly it dims")
    @Config.DefaultDouble(0.3D)
    @Config.RangeDouble(min = 0.0D, max = 0.5D)
    public static double pulseAmplitude;

    /**
     * Texture pixel dimensions.
     *
     * Set to the actual pixel size of your texture to make correct UV mapping.
     * Defaults match bar size so default behavior stays compatible.
     */
    @Config.Comment("Oxygen texture width in pixels")
    @Config.DefaultInt(81)
    @Config.RangeInt(min = 1, max = 2048)
    public static int oxygenTextureWidth;

    @Config.Comment("Oxygen texture height in pixels")
    @Config.DefaultInt(9)
    @Config.RangeInt(min = 1, max = 2048)
    public static int oxygenTextureHeight;

    @Config.Comment("Temperature texture width in pixels")
    @Config.DefaultInt(81)
    @Config.RangeInt(min = 1, max = 2048)
    public static int temperatureTextureWidth;

    @Config.Comment("Temperature texture height in pixels")
    @Config.DefaultInt(9)
    @Config.RangeInt(min = 1, max = 2048)
    public static int temperatureTextureHeight;

    public enum BarOrientation {
        VERTICAL,
        HORIZONTAL
    }
}
