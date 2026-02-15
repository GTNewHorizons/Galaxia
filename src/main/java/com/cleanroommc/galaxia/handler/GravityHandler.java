package com.cleanroommc.galaxia.handler;

import com.cleanroommc.galaxia.dimension.DimensionDef;
import com.cleanroommc.galaxia.dimension.SolarSystemRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingEvent;

public class GravityHandler {

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.entityLiving;
        if (entity.worldObj == null || entity.worldObj.isRemote) return;

        int dim = entity.dimension;
        DimensionDef def = SolarSystemRegistry.getById(dim);
        if (def == null) return;

        double g = def.gravity;

        if (!entity.onGround && !entity.isInWater()) {
            entity.motionY -= 0.08 * (g - 1.0);
        }

        if (g < 0.05f) {
            entity.fallDistance = 0;
            if (Math.abs(entity.motionY) < 0.01) {
                entity.motionY *= 0.98;
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.worldObj.isRemote) return;

        EntityPlayer player = event.player;
        int dim = player.dimension;
        DimensionDef def = SolarSystemRegistry.getById(dim);
        if (def == null) return;

        double g = def.gravity;
        if (g == 0) g = 0.0001; // Prevent division by zero, though unlikely

        double modifier = Math.sqrt(1.0 / g);
        player.jumpMovementFactor = (float) (0.02 * modifier);
        if (player.isSprinting()) {
            player.jumpMovementFactor += (float) (0.006 * modifier);
        }
    }
}
