package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.gtnewhorizons.galaxia.utility.PlanetAPI;

/**
 * Mixin to change the jump mechanics on different gravity planets
 */
@Mixin(EntityLivingBase.class)
public abstract class JumpMixin {

    /**
     * Modifies jumping based on gravity of a planet - 1g = original, 0g = 0, see inverse sqrt for all else
     * 
     * @param original The original jump value
     * @return The recalculated jump value
     */
    @ModifyConstant(method = "jump", constant = @Constant(doubleValue = 0.41999998688697815D))
    private double galaxia$modifyJump(double original) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        double g = PlanetAPI.getGravity(self);
        if (g == 1.0D) return original;
        if (g == 0) return 0;
        // Simplify: standard inverse for jump velocity to achieve similar height (h ~ v^2 / g)
        return original / Math.sqrt(g); // For low g, higher jump; for high g, lower
    }
}
