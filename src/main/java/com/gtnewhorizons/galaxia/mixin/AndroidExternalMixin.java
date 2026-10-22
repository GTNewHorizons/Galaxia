package com.gtnewhorizons.galaxia.mixin;

import java.io.File;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Pseudo
    @Mixin(targets = "micdoodle8.mods.galacticraft.core.util.WorldUtil", remap = false)
    public static class Galacticraft {

        @Inject(
            method = "teleportEntity(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ILmicdoodle8/mods/galacticraft/api/world/ITeleportType;ZLmicdoodle8/mods/galacticraft/api/prefab/entity/EntityAutoRocket;)Lnet/minecraft/entity/Entity;",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
        private static void galaxia$checkAndroidTransfer(World destinationWorld, Entity entity, int destination,
            @Coerce Object teleportType, boolean transferInventory, @Coerce Object ridingRocket,
            CallbackInfoReturnable<Entity> cir) {
            if (AndroidControl.beforeDimensionTransfer(entity, destination)) {
                cir.setReturnValue(entity);
            }
        }
    }

    @Pseudo
    @Mixin(targets = "micdoodle8.mods.galacticraft.core.client.gui.GuiHandler", remap = false)
    public static class GalacticraftInventory {

        @Inject(method = "getServerGuiElement", at = @At("RETURN"), cancellable = true, require = 1)
        private void galaxia$denyAndroidPersonalEquipment(int id, EntityPlayer player, World world, int x, int y, int z,
            CallbackInfoReturnable<Object> cir) {
            Object container = cir.getReturnValue();
            if (AndroidControl.isAndroid(player) && container != null
                && container.getClass()
                    .getName()
                    .equals("micdoodle8.mods.galacticraft.core.inventory.ContainerExtendedInventory")) {
                cir.setReturnValue(null);
            }
        }
    }
}
