package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;

import org.spongepowered.asm.mixin.Mixin;

import com.gtnewhorizons.galaxia.utility.capabilities.ZeroGRecoilProvider;

@Mixin(EntityThrowable.class)
public class MixinEntityThrowable implements ZeroGRecoilProvider {

    @Override
    public EntityLivingBase galaxia$getShootingEntity() {
        EntityThrowable throwable = (EntityThrowable) (Object) this;
        return throwable.getThrower();
    }

}
