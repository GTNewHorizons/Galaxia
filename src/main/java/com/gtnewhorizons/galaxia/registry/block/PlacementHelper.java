package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;

public class PlacementHelper {

    public static ForgeDirection placeInEveryDirection(EntityLivingBase placer) {
        if (placer.rotationPitch > 55.0F) {
            return ForgeDirection.UP;
        } else if (placer.rotationPitch < -55.0F) {
            return ForgeDirection.DOWN;
        } else {
            return placeOnlyCardinal(placer);
        }
    }

    public static ForgeDirection placeOnlyCardinal(EntityLivingBase placer) {
        int f = MathHelper.floor_double((placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        ForgeDirection[] dirs = { ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH,
            ForgeDirection.WEST };
        return dirs[f];
    }
}
