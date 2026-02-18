package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityTNTPrimed;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.gtnewhorizons.galaxia.utility.PlanetAPI;

@Mixin({ EntityItem.class, EntityFallingBlock.class, EntityTNTPrimed.class })
public abstract class UnifiedGravityMixin {

    // entities, falling sand, tnt, items etc
    @ModifyConstant(method = "onUpdate", constant = @Constant(doubleValue = 0.03999999910593033D), require = 0)
    private double galaxia$modifyEntityGravity(double original) {
        Entity self = (Entity) (Object) this;
        return original * PlanetAPI.getGravity(self);
    }
}
