package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.api.ZeroGMovementAPI;
import com.gtnewhorizons.galaxia.core.config.ConfigPlayer;
import com.gtnewhorizons.galaxia.registry.interfaces.IZeroGRecoilProvider;

@Mixin(World.class)
public class MixinThrowRecoil {

    @Inject(method = "spawnEntityInWorld", at = @At("HEAD"))
    private void onEntitySpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) return;

        if (entity instanceof IZeroGRecoilProvider provider) {
            EntityLivingBase shooter = provider.galaxia$getShootingEntity();
            if (shooter == null) return;

            if (GalaxiaAPI.getGravity(entity) != 0) return;
            if (shooter instanceof EntityPlayer ep) {
                if (GalaxiaAPI.hasZeroGMovementCapability(ep)
                    && !ConfigPlayer.ConfigPlayerGlobal.recoil_with_zerog_capabilities) return;

                ZeroGMovementAPI.addThrowRecoil(
                    shooter,
                    entity.motionX,
                    entity.motionY,
                    entity.motionZ,
                    provider.galaxia$getProjectileMass());
            }
        }
    }
}
