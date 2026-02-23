package com.gtnewhorizons.galaxia.core.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigOverlay.ConfigOverlayGlobal;
import com.gtnewhorizons.galaxia.core.config.ConfigOverlay.ConfigOverlayOxygenBar;
import com.gtnewhorizons.galaxia.core.config.ConfigOverlay.ConfigOverlayTemperatureBar;

@Config(modid = Galaxia.MODID, category = "Overlay")
@Config.LangKey("galaxia.config.category.overlay")
public class ConfigOverlay {

    @Config.LangKey("galaxia.config.category.overlay_global")
    public static final ConfigOverlayGlobal ConfigOverlayGlobal = new ConfigOverlayGlobal();

    @Config.LangKey("galaxia.config.category.overlay_global")
    public static class ConfigOverlayGlobal {

        @Config.LangKey("galaxia.config.overlay.horizontal_offset")
        @Config.DefaultInt(0)
        @Config.RangeInt(min = -200, max = 200)
        public int hudOffsetX;

        @Config.LangKey("galaxia.config.overlay.vertical_offset")
        @Config.DefaultInt(0)
        @Config.RangeInt(min = -200, max = 200)
        public int hudOffsetY;

        @Config.LangKey("galaxia.config.overlay.bars_orientation")
        @Config.DefaultEnum("HORIZONTAL")
        public BarOrientation barOrientation;

        @Config.LangKey("galaxia.config.overlay.pulse.speed")
        @Config.DefaultDouble(150.0D)
        @Config.RangeDouble(min = 50.0D, max = 500.0D)
        public double pulseSpeed;

        @Config.LangKey("galaxia.config.overlay.pulse.amplitude")
        @Config.DefaultDouble(0.3D)
        @Config.RangeDouble(min = 0.0D, max = 0.5D)
        public double pulseAmplitude;

    }

    @Config.LangKey("galaxia.config.category.overlay_oxygen_bar")
    public static final ConfigOverlayOxygenBar ConfigOverlayOxygenBar = new ConfigOverlayOxygenBar();

    @Config.LangKey("galaxia.config.category.overlay_oxygen_bar")
    public static class ConfigOverlayOxygenBar {

        @Config.LangKey("galaxia.config.overlay.show_oxygen_bar")
        @Config.DefaultBoolean(true)
        public boolean showOxygenBar;

        @Config.LangKey("galaxia.config.overlay.oxygen_bar_horizontal_offset")
        @Config.DefaultInt(87)
        @Config.RangeInt(min = -300, max = 300)
        public int oxygenOffsetX;

        @Config.LangKey("galaxia.config.overlay.oxygen_bar_vertical_offset")
        @Config.DefaultInt(0)
        @Config.RangeInt(min = -300, max = 300)
        public int oxygenOffsetY;

        @Config.LangKey("galaxia.config.overlay.oxygen_bar_critical")
        @Config.DefaultDouble(0.25D)
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double lowOxygenThreshold;

        @Config.LangKey("galaxia.config.overlay.texture.oxygen_bar_width")
        @Config.DefaultInt(81)
        @Config.RangeInt(min = 1, max = 2048)
        public int oxygenTextureWidth;

        @Config.LangKey("galaxia.config.overlay.texture.oxygen_bar_height")
        @Config.DefaultInt(9)
        @Config.RangeInt(min = 1, max = 2048)
        public int oxygenTextureHeight;

    }

    @Config.LangKey("galaxia.config.category.overlay_temperature_bar")
    public static final ConfigOverlayTemperatureBar ConfigOverlayTemperatureBar = new ConfigOverlayTemperatureBar();

    @Config.LangKey("galaxia.config.category.overlay_temperature_bar")
    public static class ConfigOverlayTemperatureBar {

        @Config.LangKey("galaxia.config.overlay.show_temperature_bar")
        @Config.DefaultBoolean(true)
        public boolean showTemperatureBar;

        @Config.LangKey("galaxia.config.overlay.temperature_bar_horizontal_offset")
        @Config.DefaultInt(-87)
        @Config.RangeInt(min = -300, max = 300)
        public int temperatureOffsetX;

        @Config.LangKey("galaxia.config.overlay.temperature_bar_vertical_offset")
        @Config.DefaultInt(0)
        @Config.RangeInt(min = -300, max = 300)
        public int temperatureOffsetY;

        @Config.LangKey("galaxia.config.overlay.temperature_bar_too_cold")
        @Config.DefaultDouble(0.35D)
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double temperatureLowThreshold;

        @Config.LangKey("galaxia.config.overlay.temperature_bar_too_hot")
        @Config.DefaultDouble(0.65D)
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double temperatureHighThreshold;

        @Config.LangKey("galaxia.config.overlay.texture.temperature_bar_width")
        @Config.DefaultInt(81)
        @Config.RangeInt(min = 1, max = 2048)
        public int temperatureTextureWidth;

        @Config.LangKey("galaxia.config.overlay.texture.temperature_bar_height")
        @Config.DefaultInt(9)
        @Config.RangeInt(min = 1, max = 2048)
        public int temperatureTextureHeight;
    }

    public enum BarOrientation {
        VERTICAL,
        HORIZONTAL
    }
}
