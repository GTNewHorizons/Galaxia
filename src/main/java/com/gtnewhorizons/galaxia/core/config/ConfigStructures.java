package com.gtnewhorizons.galaxia.core.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizons.galaxia.core.Galaxia;

@Config(modid = Galaxia.MODID, category = "Structures")
@Config.LangKey("galaxia.config.category.structures")
public class ConfigStructures {

    @Config.LangKey("galaxia.config.category.structures_enclosed")
    public static final Enclosed enclosed = new Enclosed();

    @Config.LangKey("galaxia.config.category.structures_open")
    public static final Open open = new Open();

    public static class Enclosed {

        @Config.LangKey("galaxia.config.structures.enclosed_radius")
        @Config.DefaultInt(16)
        @Config.RangeInt(min = 1, max = 511)
        public int searchRadius;
    }

    public static class Open {

        @Config.LangKey("galaxia.config.structures.open_radius")
        @Config.DefaultInt(64)
        @Config.RangeInt(min = 1, max = 511)
        public int searchRadius;
    }
}
