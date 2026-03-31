package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.block.BlockAir;
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

        if (self instanceof EntityPlayer player) {
            if (!GalaxiaAPI.hasReactionControlSystem(player)) {
                final boolean isAribourne = player.worldObj
                    .getBlock((int) player.posX, (int) (player.posY - 1), (int) player.posZ) instanceof BlockAir;
                if (!isAribourne) {
                    self.moveFlying(strafe, forward, friction);
                }
                return;
            }
        }

        // do nothing if no input
        // if (strafe == 0 && forward == 0) {
        // return;
        // }

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
        float verticalMomentum = 0;
        if (self instanceof EntityPlayer player) {
            if (player.isSneaking()) {
                verticalMomentum -= 1;
            }

            if (player instanceof EntityPlayerSP sp && sp.movementInput.jump) {
                verticalMomentum += 1;
            }
        }
        double motionX = (lookX * forward + cosYaw * strafe) * speed;
        double motionY = lookY * forward * speed + verticalMomentum * speed;
        double motionZ = (lookZ * forward + sinYaw * strafe) * speed;

        if (Math.abs(motionX) < 1e-6) motionX = 0;
        if (Math.abs(motionY) < 1e-6) motionY = 0;
        if (Math.abs(motionZ) < 1e-6) motionZ = 0;

        if (motionX * self.motionX < 0) motionX *= 2;
        if (motionY * self.motionY < 0) motionY *= 2;
        if (motionZ * self.motionZ < 0) motionZ *= 2;

        self.motionX += motionX;
        self.motionY += motionY;
        self.motionZ += motionZ;

        self.fallDistance = 0.0F;
    }
}
