package com.gtnewhorizons.galaxia.utility;

import net.minecraft.entity.Entity;

import com.gtnewhorizons.galaxia.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.dimension.EffectDef;
import com.gtnewhorizons.galaxia.dimension.SolarSystemRegistry;

public final class PlanetAPI {

    public static double getGravity(Entity e) {
        if (e == null || e.worldObj == null) return 1.0;
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return 1.0;
        return def.gravity();
        // for some cases clamping might be required
    }

    public static EffectDef getEffects(Entity e) {
        if (e == null || e.worldObj == null) return new EffectDef();
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return new EffectDef();
        return def.effects();
    }

    public static double getAirResistance(Entity e) {
        if (e == null || e.worldObj == null) return 1.0;
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return 1.0;
        return def.airResistance();
    }

    public static boolean cancelSpeed(Entity e) {
        if (e == null || e.worldObj == null) return false;
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return false;
        return def.removeSpeedCancelation();
    }
}
