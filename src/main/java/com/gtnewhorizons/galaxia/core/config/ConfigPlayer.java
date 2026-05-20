package com.gtnewhorizons.galaxia.core.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizons.galaxia.core.Galaxia;

@Config(modid = Galaxia.MODID, category = "Player")
@Config.LangKey("galaxia.config.category.player")
public class ConfigPlayer {

    @Config.LangKey("galaxia.config.category.player_global")
    public static final ConfigPlayerGlobal ConfigPlayerGlobal = new ConfigPlayerGlobal();

    @Config.LangKey("galaxia.config.category.player_tether")
    public static final ConfigTether ConfigTether = new ConfigTether();

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

    @Config.LangKey("galaxia.config.category.player_tether")
    public static class ConfigTether {

        @Config.LangKey("galaxia.config.tether.max_length")
        @Config.DefaultDouble(60.0)
        public double maxTetherLength;

        @Config.LangKey("galaxia.config.tether.max_stretch_multiplier")
        @Config.DefaultDouble(1.25)
        public double maxStretchMultiplier;

        @Config.LangKey("galaxia.config.tether.outward_damping")
        @Config.DefaultDouble(0.92)
        public double outwardDamping;

        @Config.LangKey("galaxia.config.tether.inward_assist")
        @Config.DefaultDouble(0.02)
        public double inwardAssist;

        @Config.LangKey("galaxia.config.tether.propulsion_force")
        @Config.DefaultDouble(0.08)
        public double propulsionForce;

        @Config.LangKey("galaxia.config.tether.elasticity_const")
        @Config.DefaultDouble(0.15)
        public double elasticityConst;
    }
}
