package com.gtnewhorizons.galaxia.utility;

import net.minecraft.entity.Entity;

import com.gtnewhorizons.galaxia.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.dimension.builder.EffectDef;

/**
 * API underpinning planetary mechanics
 */
public final class PlanetAPI {

    /**
     * Gets the gravity on the planet, or returns 1 if failed
     *
     * @param e The entity to check effects on
     * @return Gravity on the entity, or 1 if failed
     */
    public static double getGravity(Entity e) {
        if (e == null || e.worldObj == null) return 1.0;
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return 1.0;
        return def.gravity();
        // for some cases clamping might be required
    }

    /**
     * Gets the effects on the planet, or returns defaults if failed
     *
     * @param e The entity to check effects on
     * @return Effects on the entity, or defaults if failed
     */
    public static EffectDef getEffects(Entity e) {
        if (e == null || e.worldObj == null) return new EffectDef();
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return new EffectDef();
        return def.effects();
    }

    /**
     * Gets the air resistance on the planet, or returns 1 if failed
     *
     * @param e The entity to check effects on
     * @return Air resistance on the entity, or 1 if failed
     */
    public static double getAirResistance(Entity e) {
        if (e == null || e.worldObj == null) return 1.0;
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return 1.0;
        return def.airResistance();
    }

    /**
     * Gets whether speed is cancelled
     *
     * @param e The entity to check effects on
     * @return Boolean : True => Speed cancellation enabled
     */
    public static boolean cancelSpeed(Entity e) {
        if (e == null || e.worldObj == null) return false;
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return false;
        return def.removeSpeedCancelation();
    }
}
