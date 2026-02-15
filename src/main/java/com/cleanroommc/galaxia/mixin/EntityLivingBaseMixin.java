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
        if (res == 1.0D) return original;
        // Milder adjustment: use power to soften the effect for res >1 (denser air -> slightly stronger drag)
        return Math.pow(original, Math.sqrt(res)); // For res=1.5, ~0.96 instead of ~0.65
    }

    // air resistance horizontal
    @ModifyConstant(method = "moveEntityWithHeading", constant = @Constant(floatValue = 0.91F))
    private float galaxia$removeResistance(float original) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (self.onGround) {
            return original; // No air resistance adjustment on ground to preserve block friction
        }
        double res = PlanetAPI.getAirResistance(self);
        if (res == 1.0D) return original;
        // Milder adjustment: for res>1, slightly reduce coefficient; for res<1, slightly increase
        float adjusted = (float) Math.pow(original, Math.sqrt(res));
        // Prevent extreme values
        if (adjusted > 1.0F) adjusted = 1.0F;
        if (adjusted < 0.5F) adjusted = 0.5F;
        return PlanetAPI.cancelSpeed(self) ? 1.0F : adjusted;
    }
}
