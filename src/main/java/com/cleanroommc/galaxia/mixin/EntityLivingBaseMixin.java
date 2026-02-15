package com.cleanroommc.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.cleanroommc.galaxia.utility.PlanetAPI;

@Mixin(EntityLivingBase.class)
public abstract class EntityLivingBaseMixin {

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
        if (res < 1) return 1;
        return 1 / res;
    }

    // air resistance horizontal
    @ModifyConstant(method = "moveEntityWithHeading", constant = @Constant(floatValue = 0.91F))
    private float galaxia$removeResistance(float original) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        // if speed is canceled, replace *0.91 with *1 so it doesnt change, otherwise adjust air resistance
        double res = PlanetAPI.getAirResistance(self);
        // prevent infinite acceleration
        if (res < 1) return 1;
        float resistance = (float) (original / res);
        return PlanetAPI.cancelSpeed(self) ? 1 : resistance;
    }
}
