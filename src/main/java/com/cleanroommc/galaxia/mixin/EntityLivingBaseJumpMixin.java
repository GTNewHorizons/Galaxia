package com.cleanroommc.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.cleanroommc.galaxia.utility.PlanetAPI;

@Mixin(EntityLivingBase.class)
public abstract class EntityLivingBaseJumpMixin {

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
