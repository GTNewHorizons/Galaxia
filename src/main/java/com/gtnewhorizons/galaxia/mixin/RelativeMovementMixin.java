package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
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

        float verticalMomentum = 0;
        if (self instanceof EntityPlayer player) {
            if (!GalaxiaAPI.hasReactionControlSystem(player)) {
                // The normal accessor is not reliable at this stage
                final boolean isGrounded = player.worldObj
                    .getBlock(
                        MathHelper.floor_double(player.posX),
                        MathHelper.floor_double(player.boundingBox.minY) - 1,
                        MathHelper.floor_double(player.posZ))
                    .getMaterial()
                    .isSolid();

                if (isGrounded) {
                    self.moveFlying(strafe, forward, friction);
                }

                return;
            }
            if (player.isSneaking()) {
                verticalMomentum -= 1;
            }

            if (player instanceof EntityPlayerSP sp && sp.movementInput.jump) {
                verticalMomentum += 1;
            }
        }

        // do nothing if no input
        if (strafe == 0 && forward == 0 && verticalMomentum == 0) {
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

        // allow sprinting in space
        float speed = 0.02F * (self.isSprinting() ? 2 : 1);
        double motionX = (lookX * forward + cosYaw * strafe) * speed;
        double motionY = lookY * forward * speed + verticalMomentum * speed;
        double motionZ = (lookZ * forward + sinYaw * strafe) * speed;

        if (Math.abs(motionX) < 1e-6) motionX = 0;
        if (Math.abs(motionY) < 1e-6) motionY = 0;
        if (Math.abs(motionZ) < 1e-6) motionZ = 0;

        // Make it easier to slowdown in a given direction
        if (motionX * self.motionX < 0) motionX -= self.motionX * 0.1f;
        if (motionY * self.motionY < 0) motionY -= self.motionY * 0.1f;
        if (motionZ * self.motionZ < 0) motionZ -= self.motionZ * 0.1f;

        self.motionX += motionX;
        self.motionY += motionY;
        self.motionZ += motionZ;

        self.fallDistance = 0.0F;
    }
}
