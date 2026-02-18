package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.gtnewhorizons.galaxia.utility.PlanetAPI;

@Mixin(EntityLivingBase.class)
public abstract class GravityAirResistanceMixin {

    // gravity
    @ModifyConstant(method = "moveEntityWithHeading", constant = @Constant(doubleValue = 0.08D))
    private double galaxia$modifyGravity(double original) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        return original * PlanetAPI.getGravity(self);
    }

    // air resistance vertical
    @ModifyConstant(method = "moveEntityWithHeading", constant = @Constant(doubleValue = 0.9800000190734863D))
    private double galaxia$removeAirResistance(double original) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        double res = PlanetAPI.getAirResistance(self);
        return Math.pow(original, Math.sqrt(res));
    }

    // air resistance horizontal
    @ModifyConstant(method = "moveEntityWithHeading", constant = @Constant(floatValue = 0.91F))
    private float galaxia$removeResistance(float original) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        double res = PlanetAPI.getAirResistance(self);
        double exponent = Math.sqrt(res) * (PlanetAPI.cancelSpeed(self) ? 0.0 : 1.0);
        return (float) Math.pow(original, exponent);
    }
}
