package com.cleanroommc.galaxia.config;

import net.minecraftforge.common.config.Configuration;

public class GalaxiaConfig {
    public static int CALX_DIM_ID = 5;

    public static void init(java.io.File configFile) {
        Configuration config = new Configuration(configFile);
        config.load();
        CALX_DIM_ID = config.get("dimensions", "calxDimId", 666, "ID for calx").getInt(5);
        config.save();
    }
}
