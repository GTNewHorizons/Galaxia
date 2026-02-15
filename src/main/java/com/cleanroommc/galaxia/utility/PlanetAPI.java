package com.cleanroommc.galaxia.utility;

import net.minecraft.entity.Entity;

import com.cleanroommc.galaxia.dimension.DimensionDef;
import com.cleanroommc.galaxia.dimension.SolarSystemRegistry;

public final class PlanetAPI {

    public static double getGravity(Entity e) {
        if (e == null || e.worldObj == null) return 1.0;
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return 1.0;
        return def.gravity;
        // for some cases clamping might be required
    }

    public static double getAirResistance(Entity e) {
        if (e == null || e.worldObj == null) return 1.0;
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return 1.0;
        return def.air_resistance;
    }

    public static boolean cancelSpeed(Entity e) {
        if (e == null || e.worldObj == null) return false;
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return false;
        return def.removeSpeedCancelation;
    }
}
