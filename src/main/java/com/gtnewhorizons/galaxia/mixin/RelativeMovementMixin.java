package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.api.ZeroGMovementAPI;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Mixin that changes regular WASD motion with relative motion
 */
@Mixin(EntityLivingBase.class)
public abstract class RelativeMovementMixin {

    @Shadow
    public float prevLimbSwingAmount;

    @SideOnly(Side.CLIENT)
    private static float getClientJump(EntityPlayer player) {
        if (player instanceof EntityPlayerSP sp && sp.movementInput.jump) {
            return 1;
        }
        return 0;
    }

    @Redirect(
        method = "moveEntityWithHeading",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;moveFlying(FFF)V"))
    private void galaxia$redirectMoveFlying(EntityLivingBase self, float strafe, float forward, float friction) {
        // use vanilla method if gravity is not 0
        if (GalaxiaAPI.getGravity(self) != 0) {
            ZeroGMovementAPI.setEnabled(self, false);

            self.moveFlying(strafe, forward, friction);
            return;
        }

        float verticalMomentum = 0;
        if (self instanceof EntityPlayer player) {
            if (!GalaxiaAPI.hasZeroGMovementCapability(player)) {
                ZeroGMovementAPI.handleFallbackMovement(player, strafe, forward, friction);
                return;
            }
            if (player.isSneaking()) {
                verticalMomentum -= 1;
            }

            if (player.worldObj.isRemote) {
                verticalMomentum += getClientJump(player);
            }
        }

        ZeroGMovementAPI.setEnabled(self, true);

        // do nothing if no input
        if (strafe == 0 && forward == 0 && verticalMomentum == 0) {
            return;
        }

        ZeroGMovementAPI.handleMovement(self, strafe, forward, verticalMomentum);
    }
}
