package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;

import org.spongepowered.asm.mixin.Mixin;

import com.gtnewhorizons.galaxia.utility.capabilities.ZeroGRecoilProvider;

@Mixin(EntityArrow.class)
public class MixinEntityArrow implements ZeroGRecoilProvider {

    @Override
    public EntityLivingBase galaxia$getShootingEntity() {
        EntityArrow arrow = (EntityArrow) (Object) this;
        return (EntityLivingBase) arrow.shootingEntity;
    }
}
