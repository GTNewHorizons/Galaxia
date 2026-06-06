package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;

import org.spongepowered.asm.mixin.Mixin;

import com.gtnewhorizons.galaxia.api.ZeroGMovementAPI;
import com.gtnewhorizons.galaxia.registry.interfaces.IZeroGRecoilProvider;

@Mixin(EntityArrow.class)
public class MixinEntityArrow implements IZeroGRecoilProvider {

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
