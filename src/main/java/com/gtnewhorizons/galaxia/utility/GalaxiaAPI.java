package com.gtnewhorizons.galaxia.utility;

import static com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry.GALAXIA_DIMENSIONS;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenTank;

import baubles.api.BaublesApi;

/**
 * API underpinning planetary mechanics
 */
public final class GalaxiaAPI {

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
    public static EffectBuilder getEffects(Entity e) {
        if (e == null || e.worldObj == null) return new EffectBuilder();
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return new EffectBuilder();
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

    /**
     * @param player player
     * @return average amount of oxygen in all of player's tanks (synced with HUD bars)
     */
    public static float getPlayerOxygenLevel(EntityPlayer player) {
        float maximum = 0;
        float current = 0;
        for (int index : Galaxia.oxygenSlots) {
            ItemStack tank = BaublesApi.getBaubles(player)
                .getStackInSlot(index);
            if (tank != null && tank.getItem() instanceof ItemOxygenTank tankItem) {
                maximum += 1;
                current += tankItem.getPercentFull(tank);
            }
        }
        if (maximum == 0) return 0;
        return current / maximum;
    }

    /**
     * @param player player
     * @return player's temperature (synced with HUD bars)
     */
    public static float getPlayerTemperature(EntityPlayer player) {
        // TODO replace with proper logic
        return .5f;
    }

    public static boolean isInGalaxiaDimension(EntityPlayer player) {
        return GALAXIA_DIMENSIONS.contains(player.dimension);
    }
}
