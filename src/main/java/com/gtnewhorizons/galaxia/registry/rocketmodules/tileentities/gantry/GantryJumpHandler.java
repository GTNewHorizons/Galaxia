package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;

import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * A vanilla jump peaks a bit below 1.25 blocks, so from the top of a gantry rail, 0.75 up its block, a full block one
 * level
 * higher was just out of reach. Jumps off a rail get a tiny push
 */
public class GantryJumpHandler {

    /** Extra upward speed, lifts the peak by about 0.015 blocks */
    private static final double BOOST = 0.0025;

    @SubscribeEvent
    public void onJump(LivingJumpEvent event) {
        EntityLivingBase entity = event.entityLiving;
        if (standsOnRail(entity)) entity.motionY += BOOST;
    }

    /**
     * Whether any corner of the entity's feet rests on a Gantry Shaft
     */
    private static boolean standsOnRail(EntityLivingBase entity) {
        World world = entity.worldObj;
        int y = MathHelper.floor_double(entity.boundingBox.minY - 0.01);
        double[] xs = { entity.boundingBox.minX, entity.boundingBox.maxX };
        double[] zs = { entity.boundingBox.minZ, entity.boundingBox.maxZ };
        for (double x : xs) {
            for (double z : zs) {
                if (world.getBlock(MathHelper.floor_double(x), y, MathHelper.floor_double(z))
                    == GalaxiaBlocksEnum.GANTRY.get()) return true;
            }
        }
        return false;
    }
}
