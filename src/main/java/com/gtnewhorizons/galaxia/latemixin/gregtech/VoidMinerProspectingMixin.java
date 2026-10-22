package com.gtnewhorizons.galaxia.latemixin.gregtech;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.gtnewhorizons.galaxia.compat.GTCompat;

import bwcrossmod.galacticgreg.MTEVoidMinerBase;

@Mixin(value = MTEVoidMinerBase.class, remap = false)
public abstract class VoidMinerProspectingMixin {

    @ModifyArg(
        method = "handleOutputs",
        at = @At(
            value = "INVOKE",
            target = "Lbwcrossmod/galacticgreg/MTEVoidMinerBase;addOutputPartial(Lnet/minecraft/item/ItemStack;)V"),
        index = 0)
    private ItemStack galaxia$mineralYield(ItemStack output) {
        var base = ((MTEVoidMinerBase) (Object) this).getBaseMetaTileEntity();
        return GTCompat.prospectingOutput(base, output, base.getWorld().rand);
    }
}
