package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.gtnewhorizons.galaxia.utility.PlanetAPI;

/**
 * Mixin to change fall damage based on gravity
 */
@Mixin(EntityLivingBase.class)
public abstract class FallDamageMixin {

    /**
     * Modifies the fall distance used for damage based on gravity of the planets
     * 
     * @param distance The actual distance being fallen
     * @return The "effective" distance to be used in damange calculations
     */
    @ModifyVariable(method = "fall", at = @At("HEAD"), argsOnly = true)
    private float galaxia$modifyFallDistance(float distance) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        // fallback for getGravity is 1, so it won't affect damage
        // realistic kinetic energy scaling (damage ~ v^2 ~ g * h)
        return (float) (distance * PlanetAPI.getGravity(self));
    }
}
