package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.galaxia.utility.GalaxiaAPI;

/**
 * Mixin that changes regular WASD motion with relative motion
 */
@Mixin(EntityLivingBase.class)
public abstract class RelativeMovementMixin {

    @Redirect(
        method = "moveEntityWithHeading",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;moveFlying(FFF)V"))
    private void galaxia$redirectMoveFlying(EntityLivingBase self, float strafe, float forward, float friction) {
        // use vanilla method if gravity is not 0
        if (GalaxiaAPI.getGravity(self) != 0) {
            self.moveFlying(strafe, forward, friction);
            return;
        }

        // do nothing if no input
        if (strafe == 0 && forward == 0) {
            return;
        }

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

        // input initialisation
        float len = MathHelper.sqrt_float(strafe * strafe + forward * forward);
        if (len > 1.0F) {
            strafe /= len;
            forward /= len;
        }

        float speed = 0.02F;

        self.motionX += (lookX * forward + (double) cosYaw * strafe) * speed;
        self.motionY += lookY * forward * speed;
        self.motionZ += (lookZ * forward + (double) sinYaw * strafe) * speed;

        self.fallDistance = 0.0F;
    }
}
