package com.gtnewhorizons.galaxia.core.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizons.galaxia.core.Galaxia;

@Config(modid = Galaxia.MODID, category = "Dimensions")
@Config.LangKey("galaxia.config.category.dimensions")
@Config.RequiresMcRestart
public class ConfigDimensions {

    @Config.Comment("First dimension id of the generated orbit dimensions, every playable body takes the next one")
    @Config.LangKey("galaxia.config.dimensions.orbit_base_id")
    @Config.DefaultInt(600)
    @Config.RangeInt(min = 2, max = 32000)
    public static int orbitBaseId;
}
