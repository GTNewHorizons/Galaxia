package com.gtnewhorizons.galaxia.core.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizons.galaxia.core.Galaxia;

@Config(modid = Galaxia.MODID, category = "Player")
@Config.LangKey("galaxia.config.category.player")
public class ConfigPlayer {

    @Config.LangKey("galaxia.config.category.player_global")
    public static final ConfigPlayerGlobal ConfigPlayerGlobal = new ConfigPlayerGlobal();

    @Config.LangKey("galaxia.config.category.player_global")
    public static class ConfigPlayerGlobal {

        @Config.LangKey("galaxia.config.player.debuff_creative")
        @Config.DefaultBoolean(false)
        public boolean applyDebuffsInCreative;
    }
}
