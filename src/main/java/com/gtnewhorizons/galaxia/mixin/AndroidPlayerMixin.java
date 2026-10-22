package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryEnderChest;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.FoodStats;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.galaxia.registry.android.AndroidControl;
import com.gtnewhorizons.galaxia.registry.android.EntityBody;

@Mixin(EntityPlayerMP.class)
public class AndroidPlayerMixin {

    @Inject(method = "displayGUIChest", at = @At("HEAD"), cancellable = true, require = 1)
    private void galaxia$denyAndroidEnderChest(IInventory inventory, CallbackInfo ci) {
        if (inventory instanceof InventoryEnderChest && AndroidControl.isAndroid((EntityPlayerMP) (Object) this))
            ci.cancel();
    }

    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true, require = 1)
    private void galaxia$returnInsteadOfDeath(DamageSource source, CallbackInfo ci) {
        if (AndroidControl.interceptDeath((EntityPlayerMP) (Object) this, source)) ci.cancel();
    }

    @Inject(method = "travelToDimension", at = @At("HEAD"), cancellable = true, require = 1)
    private void galaxia$keepAndroidLocal(int destination, CallbackInfo ci) {
        EntityPlayerMP player = (EntityPlayerMP) (Object) this;
        int actualDestination = player.dimension == 1 && destination == 1 ? 0 : destination;
        if (AndroidControl.beforeDimensionTransfer(player, actualDestination)) ci.cancel();
    }

    @Mixin(Entity.class)
    public static class Transfers {

        @Inject(method = "travelToDimension", at = @At("HEAD"), cancellable = true, require = 1)
        private void galaxia$keepBodyLocal(int destination, CallbackInfo ci) {
            if (AndroidControl.beforeDimensionTransfer((Entity) (Object) this, destination)) ci.cancel();
        }
    }

    @Mixin(ServerConfigurationManager.class)
    public static class PlayerData {

        @Inject(method = "readPlayerDataFromFile", at = @At("RETURN"), require = 1)
        private void galaxia$restoreCanonicalBody(EntityPlayerMP player, CallbackInfoReturnable<NBTTagCompound> cir) {
            AndroidControl.hydrateLogin(player, cir.getReturnValue());
        }

        @Inject(
            method = "transferPlayerToDimension(Lnet/minecraft/entity/player/EntityPlayerMP;ILnet/minecraft/world/Teleporter;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false)
        private void galaxia$guardPlayerTransfer(EntityPlayerMP player, int destination, Teleporter teleporter,
            CallbackInfo ci) {
            if (AndroidControl.beforeDimensionTransfer(player, destination)) ci.cancel();
        }

        @Inject(
            method = "transferEntityToWorld(Lnet/minecraft/entity/Entity;ILnet/minecraft/world/WorldServer;Lnet/minecraft/world/WorldServer;Lnet/minecraft/world/Teleporter;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false)
        private void galaxia$guardEntityTransfer(Entity entity, int source, WorldServer oldWorld, WorldServer newWorld,
            Teleporter teleporter, CallbackInfo ci) {
            if (AndroidControl.beforeDimensionTransfer(entity, newWorld.provider.dimensionId)) ci.cancel();
        }
    }

    @Mixin(EntityPlayer.class)
    public static class PlayerMarker {

        @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true, require = 1)
        private void galaxia$metallicAndroidDamage(CallbackInfoReturnable<String> cir) {
            if (AndroidControl.isAndroid((EntityPlayer) (Object) this))
                cir.setReturnValue(EntityBody.ANDROID_HURT_SOUND);
        }

        @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true, require = 1)
        private void galaxia$metallicAndroidDeath(CallbackInfoReturnable<String> cir) {
            if (AndroidControl.isAndroid((EntityPlayer) (Object) this))
                cir.setReturnValue(EntityBody.ANDROID_DEATH_SOUND);
        }

        @Inject(
            method = "setSpawnChunk(Lnet/minecraft/util/ChunkCoordinates;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
        private void galaxia$keepBiologicalSpawn(ChunkCoordinates coordinates, boolean forced, CallbackInfo ci) {
            if (AndroidControl.isAndroid((EntityPlayer) (Object) this)) ci.cancel();
        }

        @Inject(
            method = "setSpawnChunk(Lnet/minecraft/util/ChunkCoordinates;ZI)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false)
        private void galaxia$keepBiologicalDimensionSpawn(ChunkCoordinates coordinates, boolean forced, int dimension,
            CallbackInfo ci) {
            if (AndroidControl.isAndroid((EntityPlayer) (Object) this)) ci.cancel();
        }

        @Inject(method = "writeEntityToNBT", at = @At("RETURN"), require = 1)
        private void galaxia$markCanonicalBody(NBTTagCompound tag, CallbackInfo ci) {
            if (AndroidControl.isManaged((EntityPlayer) (Object) this)) tag.setBoolean("galaxiaManagedBody", true);
        }
    }

    @Mixin(EntityLivingBase.class)
    public static class Health {

        @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true, require = 1)
        private void galaxia$materialRepairOnly(float health, CallbackInfo ci) {
            if (!AndroidControl.allowsHealthChange((EntityLivingBase) (Object) this, health)) ci.cancel();
        }

        @Inject(method = "canBreatheUnderwater", at = @At("HEAD"), cancellable = true, require = 1)
        private void galaxia$androidNeedsNoAir(CallbackInfoReturnable<Boolean> cir) {
            if (AndroidControl.isAndroid((EntityLivingBase) (Object) this)) cir.setReturnValue(true);
        }
    }

    @Mixin(FoodStats.class)
    public static class Food {

        @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true, require = 1)
        private void galaxia$noAndroidMetabolism(EntityPlayer player, CallbackInfo ci) {
            if (AndroidControl.isAndroid(player)) ci.cancel();
        }
    }
}
