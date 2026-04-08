package com.gtnewhorizons.galaxia.utility;

import javax.annotation.Nonnull;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

import com.gtnewhorizons.galaxia.core.config.ConfigPlayer;

public final class ZeroGMovementAPI {

    public static final double THROW_RECOIL_FACTOR = 0.2;
    public static final double MAX_RECOIL = 1.0;
    public static final double DEFAULT_MASS = 1.0;

    private ZeroGMovementAPI() {}

    public static void handleMovement(@Nonnull EntityLivingBase self, float strafe, float forward, float vertical) {
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
        double motionY = lookY * forward * speed + vertical * speed;
        double motionZ = (lookZ * forward + sinYaw * strafe) * speed;

        // Make it easier to slowdown in a given direction
        if (motionX * self.motionX < 0) motionX -= self.motionX * 0.1f;
        if (motionY * self.motionY < 0) motionY -= self.motionY * 0.1f;
        if (motionZ * self.motionZ < 0) motionZ -= self.motionZ * 0.1f;

        addMotion(self, motionX, motionY, motionZ);

        self.fallDistance = 0.0F;
    }

    public static void setEnabled(@Nonnull EntityLivingBase self, boolean cap) {
        // Don't send capabilities to the client since we don't want double-tap to fly behavior like in creative
        if (self instanceof EntityPlayer player && !player.worldObj.isRemote && !player.capabilities.allowFlying) {
            player.capabilities.allowFlying = cap;
            player.capabilities.isFlying = cap;
        }
    }

    public static void handleFallbackMovement(@Nonnull EntityPlayer player, float strafe, float forward,
        float friction) {
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

    public static void addThrowRecoil(@Nonnull EntityLivingBase entity, double motionX, double motionY, double motionZ,
        double mass) {
        final double length = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        if (length <= 0) return;

        final double dirX = motionX / length;
        final double dirY = motionY / length;
        final double dirZ = motionZ / length;

        final double scaledLength = MathHelper.clamp_double(length * THROW_RECOIL_FACTOR * mass, 0, MAX_RECOIL);

        final double recoilX = dirX * scaledLength;
        final double recoilY = dirY * scaledLength;
        final double recoilZ = dirZ * scaledLength;

        // apply opposite momentum
        addMotion(entity, -recoilX, -recoilY, -recoilZ);

        entity.velocityChanged = true;
    }

    private static void addMotion(@Nonnull EntityLivingBase entity, double motionX, double motionY, double motionZ) {
        final double max_speed = ConfigPlayer.ConfigPlayerGlobal.max_zerog_speed;
        entity.motionX = MathHelper.clamp_double(entity.motionX + motionX, -max_speed, max_speed);
        entity.motionY = MathHelper.clamp_double(entity.motionY + motionY, -max_speed, max_speed);
        entity.motionZ = MathHelper.clamp_double(entity.motionZ + motionZ, -max_speed, max_speed);
    }
}
