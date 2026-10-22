package com.gtnewhorizons.galaxia.mixin;

import java.io.File;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.galaxia.registry.android.AndroidControl;

@Pseudo
@Mixin(targets = "baubles.common.lib.PlayerHandler", remap = false)
public class AndroidExternalMixin {

    @Inject(
        method = { "loadPlayerBaubles(Lnet/minecraft/entity/player/EntityPlayer;Ljava/io/File;Ljava/io/File;)V",
            "savePlayerBaubles(Lnet/minecraft/entity/player/EntityPlayer;Ljava/io/File;Ljava/io/File;)V" },
        at = @At("HEAD"),
        cancellable = true,
        require = 2)
    private static void galaxia$keepManagedEquipment(EntityPlayer player, File mainFile, File backupFile,
        CallbackInfo ci) {
        if (player != null && AndroidControl.isManaged(player)) {
            ci.cancel();
        }
    }

}
