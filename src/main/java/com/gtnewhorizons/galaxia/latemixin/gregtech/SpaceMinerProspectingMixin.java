package com.gtnewhorizons.galaxia.latemixin.gregtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.gtnewhorizons.galaxia.compat.GTCompat;

import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleMiner;

@Mixin(value = TileEntityModuleMiner.class, remap = false)
public abstract class SpaceMinerProspectingMixin {

    @ModifyArg(
        method = "checkProcessing_EM",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;merge(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        index = 1)
    private Object galaxia$mineralYield(Object count) {
        var base = ((TileEntityModuleMiner) (Object) this).getBaseMetaTileEntity();
        return (Long) count * GTCompat.prospectingYieldFactor(base, base.getWorld().rand);
    }
}
