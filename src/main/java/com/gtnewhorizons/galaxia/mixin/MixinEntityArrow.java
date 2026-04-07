package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;

import org.spongepowered.asm.mixin.Mixin;

import com.gtnewhorizons.galaxia.utility.ZeroGMovementAPI;
import com.gtnewhorizons.galaxia.utility.capabilities.ZeroGRecoilProvider;

@Mixin(EntityArrow.class)
public class MixinEntityArrow implements ZeroGRecoilProvider {

    @Override
    public double galaxia$getProjectileMass() {
        return ZeroGMovementAPI.DEFAULT_MASS * 0.5D;
    }

    @Override
    public EntityLivingBase galaxia$getShootingEntity() {
        EntityArrow arrow = (EntityArrow) (Object) this;
        return (EntityLivingBase) arrow.shootingEntity;
    }
}
