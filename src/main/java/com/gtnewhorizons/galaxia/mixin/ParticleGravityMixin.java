package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.gtnewhorizons.galaxia.utility.PlanetAPI;

@Mixin({ EntityFX.class })
public abstract class ParticleGravityMixin {

    @ModifyConstant(method = "onUpdate", constant = @Constant(doubleValue = 0.04D), require = 0)
    private double galaxia$modifyParticleGravity(double original) {
        Entity self = (Entity) (Object) this;
        return original * PlanetAPI.getGravity(self);
    }
}
