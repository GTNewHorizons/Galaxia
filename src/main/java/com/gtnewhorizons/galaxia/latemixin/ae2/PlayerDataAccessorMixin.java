package com.gtnewhorizons.galaxia.latemixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.gtnewhorizons.galaxia.compat.ae2.IPlayerMappingAccess;

import appeng.core.worlddata.IWorldPlayerMapping;

@Mixin(targets = "appeng.core.worlddata.PlayerData", remap = false)
public interface PlayerDataAccessorMixin extends IPlayerMappingAccess {

    @Override
    @Accessor("playerMapping")
    IWorldPlayerMapping galaxia$getPlayerMapping();
}
