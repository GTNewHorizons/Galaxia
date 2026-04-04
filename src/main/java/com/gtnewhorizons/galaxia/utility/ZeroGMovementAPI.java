package com.gtnewhorizons.galaxia.utility;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

public final class ZeroGMovementAPI {

    private ZeroGMovementAPI() {}

    public static void handleMovement(EntityLivingBase self, float strafe, float forward, float vertical) {
        float yawRad = self.rotationYaw * (float) Math.PI / 180.0F;
        float pitchRad = self.rotationPitch * (float) Math.PI / 180.0F;

        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);

        // vector of vision
        double lookX = -sinYaw * cosPitch;
        double lookY = -sinPitch;
        double lookZ = cosYaw * cosPitch;

        // input initialization
        float len = MathHelper.sqrt_float(strafe * strafe + forward * forward);
        if (len > 1.0F) {
            strafe /= len;
            forward /= len;
        }

        // allow sprinting in space
        float speed = 0.02F * (self.isSprinting() ? 2 : 1);
        double motionX = (lookX * forward + cosYaw * strafe) * speed;
        double motionY = lookY * forward * speed + vertical* speed;
        double motionZ = (lookZ * forward + sinYaw * strafe) * speed;

        // Make it easier to slowdown in a given direction
        if (motionX * self.motionX < 0) motionX -= self.motionX * 0.1f;
        if (motionY * self.motionY < 0) motionY -= self.motionY * 0.1f;
        if (motionZ * self.motionZ < 0) motionZ -= self.motionZ * 0.1f;

        self.motionX += motionX;
        self.motionY += motionY;
        self.motionZ += motionZ;

        self.fallDistance = 0.0F;
    }

    public static void setEnabled(EntityLivingBase self, boolean cap)  {
        // Don't send capabilities to the client since we don't want double-tap to fly behavior like in creative
        if (self instanceof EntityPlayer player && !player.worldObj.isRemote && !player.capabilities.allowFlying) {
            player.capabilities.allowFlying = cap;
            player.capabilities.isFlying = cap;
        }
    }

    public static void handleFallbackMovement(EntityPlayer player, float strafe, float forward, float friction) {
        // The normal accessor is not reliable at this stage
        final boolean isGrounded = player.worldObj
            .getBlock(
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.boundingBox.minY) - 1,
                MathHelper.floor_double(player.posZ))
            .getMaterial()
            .isSolid();

        if (isGrounded) {
            player.moveFlying(strafe, forward, friction);
        }
    }
}
