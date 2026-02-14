package com.cleanroommc.galaxia.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class GalaxiaConfig {
    public static int CALX_DIM_ID = 5;

    public static void init(File configFile) {
        Configuration config = new Configuration(configFile);
        config.load();
        CALX_DIM_ID = config.get("dimensions", "calxDimId", 666, "ID for calx").getInt(5);
        config.save();
    }
}
