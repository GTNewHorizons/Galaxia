package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.galaxia.client.AndroidClient;

@Mixin(AbstractClientPlayer.class)
public abstract class AndroidSkinMixin {

    @Inject(method = "getLocationSkin()Lnet/minecraft/util/ResourceLocation;", at = @At("HEAD"), cancellable = true)
    private void galaxia$androidSkin(CallbackInfoReturnable<ResourceLocation> callback) {
        if (AndroidClient.isAndroid((AbstractClientPlayer) (Object) this)) {
            callback.setReturnValue(AndroidClient.ROBOT_SKIN);
        }
    }

    @Inject(method = "func_152122_n()Z", at = @At("HEAD"), cancellable = true)
    private void galaxia$hideAndroidCape(CallbackInfoReturnable<Boolean> callback) {
        if (AndroidClient.isAndroid((AbstractClientPlayer) (Object) this)) callback.setReturnValue(false);
    }

    @Inject(method = "getLocationCape()Lnet/minecraft/util/ResourceLocation;", at = @At("HEAD"), cancellable = true)
    private void galaxia$androidCape(CallbackInfoReturnable<ResourceLocation> callback) {
        if (AndroidClient.isAndroid((AbstractClientPlayer) (Object) this)) callback.setReturnValue(null);
    }
}
