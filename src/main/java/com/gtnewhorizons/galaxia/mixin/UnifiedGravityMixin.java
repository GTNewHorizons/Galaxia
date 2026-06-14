package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntityThrowable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;

/**
 * Mixin to unify gravity for miscellaneous entities such as TNT
 */
@Mixin({ EntityItem.class, EntityTNTPrimed.class, EntityFishHook.class, EntityMinecart.class, EntityFallingBlock.class,
    EntityBoat.class, EntityThrowable.class, EntityFX.class, EntityXPOrb.class, EntityFireball.class })
public abstract class UnifiedGravityMixin {

    /**
     * Modifies the fall rate of specific entities with falling properties such as items, falling sand or tnt
     *
     * @param original The original fall rate
     * @return The recalculated fall rate
     */
    @ModifyConstant(method = "onUpdate", constant = @Constant(doubleValue = 0.03999999910593033D), require = 0)
    private double galaxia$modifyEntityGravity(double original) {
        Entity self = (Entity) (Object) this;
        return original * GalaxiaAPI.getGravity(self);
    }
}
