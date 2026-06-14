package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntityThrowable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;

/**
 * Mixin to unify gravity for miscellaneous entities such as TNT
 */
public class UnifiedGravityMixin {

    @Mixin({ EntityItem.class, EntityTNTPrimed.class, EntityFishHook.class, EntityMinecart.class,
        EntityFallingBlock.class, EntityBoat.class })
    public abstract static class NormalEntities {

        @ModifyConstant(method = "onUpdate", constant = @Constant(doubleValue = 0.03999999910593033D), require = 0)
        private double galaxia$modifyEntityGravity(double original) {
            return original * GalaxiaAPI.getGravity((Entity) (Object) this);
        }
    }

    @Mixin({ EntityArrow.class })
    public abstract static class ArrowEntities {

        @ModifyConstant(method = "onUpdate", constant = @Constant(floatValue = 0.05F), require = 0)
        private float galaxia$modifyEntityGravity(float original) {
            return (float) (original * GalaxiaAPI.getGravity((Entity) (Object) this));
        }

        @ModifyConstant(
            method = "onUpdate()V",
            constant = @Constant(doubleValue = -0.10000000149011612D, ordinal = 1),
            require = 0)
        private double galaxia$modifyBounceDampening(double original) {
            return original * GalaxiaAPI.getGravity((Entity) (Object) this);
        }
    }

    @Mixin({ EntityThrowable.class })
    public abstract static class ThrowableEntities {

        @ModifyConstant(method = "getGravityVelocity", constant = @Constant(floatValue = 0.03F), require = 0)
        private float galaxia$modifyEntityGravity(float original) {
            return (float) (original * GalaxiaAPI.getGravity((Entity) (Object) this));
        }
    }

    @Mixin({ EntityFX.class })
    public abstract static class ParticleGravityMixin {

        @ModifyConstant(method = "onUpdate", constant = @Constant(doubleValue = 0.04D), require = 0)
        private double galaxia$modifyParticleGravity(double original) {
            return original * GalaxiaAPI.getGravity((Entity) (Object) this);
        }
    }
}
