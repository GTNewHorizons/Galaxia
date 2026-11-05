package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityFireAccessor {

    @Accessor("fire")
    int galaxia$getFireTicks();

    @Accessor("fire")
    void galaxia$setFireTicks(int ticks);
}
