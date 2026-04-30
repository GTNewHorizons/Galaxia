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

        @Config.LangKey("galaxia.config.player.zero_g_movement")
        @Config.DefaultBoolean(true)
        public boolean applyZeroGravityMovement;

        @Config.LangKey("galaxia.config.player.max_zerog_speed")
        @Config.DefaultDouble(Double.MAX_VALUE)
        public double max_zerog_speed;

        @Config.LangKey("galaxia.config.player.rcs_zerog_recoil")
        @Config.DefaultBoolean(true)
        public boolean recoil_with_zerog_capabilities;
    }
}
