package com.gtnewhorizons.galaxia.core.config;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

/**
 * Class for easier config registration to unclog ClientProxy class
 */
public class ConfigMain {

    public static void RegisterGalaxiaConfig() {
        try {
            ConfigurationManager.registerConfig(ConfigOverlay.class);
            ConfigurationManager.registerConfig(ConfigRocket.class);
            ConfigurationManager.registerConfig(ConfigPlayer.class);
            ConfigurationManager.registerConfig(ConfigMachines.class);
            ConfigurationManager.registerConfig(ConfigStructures.class);
        } catch (Exception ignored) {}
    }
}
