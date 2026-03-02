package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.gtnewhorizons.galaxia.utility.GalaxiaAPI;
import com.gtnewhorizons.galaxia.utility.effects.GalaxiaEffectAPI;

/**
 * Mixin to deal with gravity and air resistance
 */
@Mixin(EntityLivingBase.class)
public abstract class GravityAirResistanceMixin {

    private static final float VANILLA_FRICTION = 0.91F;
    private float galaxia$currentFriction = VANILLA_FRICTION;

    /**
     * Modifies the fall rate of entities based on gravity of the dimension
     *
     * @param original The original fall rate
     * @return The new recalculated fall rate
     */
    @ModifyConstant(method = "moveEntityWithHeading", constant = @Constant(doubleValue = 0.08D))
    private double galaxia$modifyGravity(double original) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        return original * GalaxiaAPI.getGravity(self);
    }

    /**
     * Modifies the vertical air resistance based on air resistance from API
     *
     * @param original The original vertical air resistance
     * @return The new recalculated vertical air resistance
     */
    @ModifyConstant(method = "moveEntityWithHeading", constant = @Constant(doubleValue = 0.9800000190734863D))
    private double galaxia$removeAirResistance(double original) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        double res = GalaxiaAPI.getAirResistance(self);
        return Math.pow(original, Math.sqrt(res));
    }

    /**
     * Modifies the horizontal air resistance based on air resistance from API
     * <p>
     * THIS METHOD MUST RETURN VALUE LESS THAN 1 OTHERWISE PLAYER WILL BE SENT INTO ABYSS
     *
     * @param original The original horizontal air resistance
     * @return The new recalculated horizontal air resistance
     */
    @ModifyConstant(method = "moveEntityWithHeading", constant = @Constant(floatValue = 0.91F))
    private float galaxia$removeResistance(float original) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        double res = GalaxiaAPI.getAirResistance(self);
        double exponent = Math.sqrt(res) * (GalaxiaAPI.cancelSpeed(self) ? 0.0 : 1.0);
        float newFriction = (float) Math.pow(original, exponent) * GalaxiaEffectAPI.getSpeedMultiplier(self);
        this.galaxia$currentFriction = newFriction;
        return newFriction;
    }

    /**
     * Modifies value that defines friction with surface so with Galaxia effects you don't walk with insane speed
     */
    @ModifyVariable(method = "moveEntityWithHeading", at = @At(value = "STORE", ordinal = 0), name = "f3")
    private float galaxia$compensateMoveSpeed(float f3) {
        float vanillaCubed = VANILLA_FRICTION * VANILLA_FRICTION * VANILLA_FRICTION;
        float newCubed = galaxia$currentFriction * galaxia$currentFriction * galaxia$currentFriction;

        if (newCubed == 0.0f) {
            newCubed = 0.0001f;
        }

        float compensationScale = vanillaCubed / newCubed;

        f3 *= compensationScale;

        if (f3 > 1.0f) f3 = 1.0f;
        if (f3 < 0.01f) f3 = 0.01f;

        return f3;
    }
}
