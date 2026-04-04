package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityFireball;

import org.spongepowered.asm.mixin.Mixin;

import com.gtnewhorizons.galaxia.utility.capabilities.ZeroGRecoilProvider;

@Mixin(EntityFireball.class)
public class MixinEntityFireBall implements ZeroGRecoilProvider {

    @Override
    public EntityLivingBase galaxia$getShootingEntity() {
        EntityFireball fireball = (EntityFireball) (Object) this;
        return fireball.shootingEntity;
    }
}
