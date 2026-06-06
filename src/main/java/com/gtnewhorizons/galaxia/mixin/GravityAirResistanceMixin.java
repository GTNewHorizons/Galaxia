package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.gtnewhorizons.galaxia.api.GalaxiaEffectAPI;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;

/**
 * Mixin to deal with gravity and air resistance
 */
@Mixin(EntityLivingBase.class)
public abstract class GravityAirResistanceMixin {

    private static final float VANILLA_FRICTION = 0.91F;
    private float galaxia$currentFriction = VANILLA_FRICTION;

    private int galaxia$cachedDim = Integer.MIN_VALUE;
    private DimensionDef galaxia$cachedDef;

    private DimensionDef galaxia$getDef(EntityLivingBase self) {
        if (self.worldObj == null) return null;
        int dim = self.dimension;
        if (dim != galaxia$cachedDim) {
            galaxia$cachedDim = dim;
            galaxia$cachedDef = SolarSystemRegistry.getById(dim);
        }
        return galaxia$cachedDef;
    }

    /**
     * Modifies the fall rate of entities based on gravity of the dimension
     *
     * @param original The original fall rate
     * @return The new recalculated fall rate
     */
    @ModifyConstant(method = "moveEntityWithHeading", constant = @Constant(doubleValue = 0.08D))
    private double galaxia$modifyGravity(double original) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        DimensionDef def = galaxia$getDef(self);
        if (def == null) return original;
        return original * def.gravity();
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
        DimensionDef def = galaxia$getDef(self);
        if (def == null) return original;
        return Math.pow(original, Math.sqrt(def.airResistance()));
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
        DimensionDef def = galaxia$getDef(self);
        if (def == null) {
            this.galaxia$currentFriction = original;
            return original;
        }
        double exponent = Math.sqrt(def.airResistance()) * (def.removeSpeedCancelation() ? 0.0 : 1.0);
        float newFriction = (float) Math.pow(original * GalaxiaEffectAPI.getSpeedMultiplier(self), exponent);
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
