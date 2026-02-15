package com.cleanroommc.galaxia.handler;

import com.cleanroommc.galaxia.dimension.DimensionDef;
import com.cleanroommc.galaxia.dimension.SolarSystemRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingEvent;

import java.util.WeakHashMap;

public class GravityHandler {

    private final WeakHashMap<EntityLivingBase, Double> pendingJump = new WeakHashMap<>();

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase e = event.entityLiving;
        if (e.worldObj == null || e.worldObj.isRemote) return;
        if (e instanceof EntityPlayer && ((EntityPlayer) e).capabilities.isCreativeMode) return;

        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return;

        double g = clamp(def.gravity, 0.01, 50.0);

        Double mul = pendingJump.remove(e);
        if (mul != null) {
            e.motionY *= mul;
        }

        if (!e.onGround && !e.isInWater()) {
            e.motionY += 0.08D * (1.0D - g);
            double terminal = -3.0D * g;
            if (e.motionY < terminal) e.motionY = terminal;
        } else {
            if (e.onGround) e.fallDistance = 0;
        }

        e.fallDistance *= g;

        if (e.isCollidedVertically && e.motionY < 0) {
            e.motionY = 0;
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        EntityPlayer p = event.player;
        if (p.worldObj == null || p.worldObj.isRemote) return;
        if (p.capabilities.isCreativeMode) return;

        DimensionDef def = SolarSystemRegistry.getById(p.dimension);
        if (def == null) return;

        double g = clamp(def.gravity, 0.01, 50.0);

        p.jumpMovementFactor = (float) (0.02 / Math.sqrt(g));

        p.motionY += 0.08D * (1.0D - g);
        double terminal = -3.0D * g;
        if (p.motionY < terminal) p.motionY = terminal;

        p.fallDistance *= g;

        if (p.isCollidedVertically && p.motionY < 0) p.motionY = 0;
    }

    @SubscribeEvent
    public void onLivingJump(LivingEvent.LivingJumpEvent event) {
        EntityLivingBase e = event.entityLiving;
        if (e.worldObj == null || e.worldObj.isRemote) return;
        if (e instanceof EntityPlayer && ((EntityPlayer) e).capabilities.isCreativeMode) return;

        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return;

        double g = clamp(def.gravity, 0.01, 50.0);

        double mul = Math.sqrt(1.0D / g);
        pendingJump.put(e, mul);
    }

    private double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
