package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;

import org.spongepowered.asm.mixin.Mixin;

import com.gtnewhorizons.galaxia.api.ZeroGMovementAPI;
import com.gtnewhorizons.galaxia.registry.capabilities.ZeroGRecoilProvider;

@Mixin(EntityThrowable.class)
public class MixinEntityThrowable implements ZeroGRecoilProvider {

    @Override
    public double galaxia$getProjectileMass() {
        return ZeroGMovementAPI.DEFAULT_MASS;
    }

    @Override
    public EntityLivingBase galaxia$getShootingEntity() {
        EntityThrowable throwable = (EntityThrowable) (Object) this;
        return throwable.getThrower();
    }

}
