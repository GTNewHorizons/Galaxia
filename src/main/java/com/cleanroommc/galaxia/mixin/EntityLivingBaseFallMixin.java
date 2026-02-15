package com.cleanroommc.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.cleanroommc.galaxia.utility.PlanetAPI;

@Mixin(EntityLivingBase.class)
public abstract class EntityLivingBaseFallMixin {

    @ModifyVariable(method = "fall", at = @At("HEAD"), argsOnly = true)
    private float galaxia$modifyFallDistance(float distance) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        // fallback for getGravity is 1, so it won't affect damage
        // realistic kinetic energy scaling (damage ~ v^2 ~ g * h)
        return (float) (distance * PlanetAPI.getGravity(self));
    }
}
