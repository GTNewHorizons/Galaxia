package com.gtnewhorizons.galaxia.core.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizons.galaxia.core.Galaxia;

@Config(modid = Galaxia.MODID, category = "Rocket")
@Config.LangKey("galaxia.config.category.rocket")
public class ConfigRocket {

    @Config.LangKey("galaxia.config.category.rocket_global")
    public static final ConfigRocketGlobal ConfigRocketGlobal = new ConfigRocketGlobal();

    @Config.LangKey("galaxia.config.category.rocket_global")
    public static class ConfigRocketGlobal {

        @Config.LangKey("galaxia.config.rocket.camera_distance")
        @Config.DefaultInt(20)
        @Config.RangeInt(min = 1, max = 100)
        public int rocketCameraDistance;
    }
}
