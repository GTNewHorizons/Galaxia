package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.gtnewhorizons.galaxia.utility.effects.GalaxiaEffectAPI;

/**
 * Mixin to change the jump mechanics on different gravity planets
 */
@Mixin(EntityLivingBase.class)
public abstract class JumpMixin {

    /**
     * Modifies jumping based on gravity of a planet
     *
     * @param original The original jump value
     * @return The recalculated jump value
     */
    @ModifyConstant(method = "jump", constant = @Constant(doubleValue = 0.41999998688697815D))
    private double galaxia$modifyJump(double original) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        // if player is in galaxia dimension, his jump height doesn't increase because gravity handles it already
        return original * GalaxiaEffectAPI.getSpeedMultiplier(self);
    }
}
