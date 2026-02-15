package com.cleanroommc.galaxia.mixin;

import net.minecraft.entity.item.EntityItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.cleanroommc.galaxia.utility.PlanetAPI;

@Mixin(EntityItem.class)
public abstract class EntityItemGravityMixin {

    @ModifyConstant(method = "onUpdate", constant = @Constant(doubleValue = 0.03999999910593033D))
    private double galaxia$modifyItemGravity(double original) {
        EntityItem self = (EntityItem) (Object) this;
        // fallback for getGravity is 1, so it won't affect gravity
        return original * PlanetAPI.getGravity(self);
    }
}
