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
        if (g == 1) return original;
        // adding + 0.1 to account weight of a player (and prevent division by 0)
        return original * (1 / g + 0.1 * (1 - (int) Math.abs(Math.signum(g))));
    }
}
