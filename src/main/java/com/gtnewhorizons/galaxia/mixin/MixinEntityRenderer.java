package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.galaxia.core.config.ConfigRocket;
import com.gtnewhorizons.galaxia.rocketmodules.entities.EntityRocket;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @Shadow
    private float thirdPersonDistance;
    @Shadow
    private float thirdPersonDistanceTemp;

    private static float originalThirdPersonDistance = -1.0F;

    @Inject(method = "setupCameraTransform", at = @At("HEAD"))
    private void galaxia$adjustRocketCamera(float partialTicks, int pass, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        boolean isInRocket = mc.thePlayer.ridingEntity instanceof EntityRocket;

        // save original values passed into the method to not accidentally override other mixins
        if (isInRocket) {
            if (originalThirdPersonDistance < 0) {
                originalThirdPersonDistance = this.thirdPersonDistance;
            }

            this.thirdPersonDistance = ConfigRocket.ConfigRocketGlobal.rocketCameraDistance;
            this.thirdPersonDistanceTemp = ConfigRocket.ConfigRocketGlobal.rocketCameraDistance;

        } else {
            if (originalThirdPersonDistance >= 0) {
                this.thirdPersonDistance = originalThirdPersonDistance;
                this.thirdPersonDistanceTemp = originalThirdPersonDistance;
            }
        }
    }
}
