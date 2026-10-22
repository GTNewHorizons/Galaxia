package com.gtnewhorizons.galaxia.latemixin.gregtech;

import java.util.List;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.galaxia.compat.GTCompat;

import gregtech.api.objects.XSTR;
import gregtech.common.tileentities.machines.multi.MTEOreDrillingPlantBase;

@Mixin(value = MTEOreDrillingPlantBase.class, remap = false)
public abstract class OreDrillProspectingMixin {

    @Shadow
    @Final
    private XSTR random;

    @Inject(method = "getOutputByDrops", at = @At("RETURN"), cancellable = true)
    private void galaxia$mineralYield(List<ItemStack> drops, CallbackInfoReturnable<ItemStack[]> cir) {
        ItemStack[] output = cir.getReturnValue()
            .clone();
        var base = ((MTEOreDrillingPlantBase) (Object) this).getBaseMetaTileEntity();
        // The drill restores this RNG between simulation and execution, including these additional yield rolls.
        for (int i = 0; i < output.length; i++) output[i] = GTCompat.prospectingOutput(base, output[i], random);
        cir.setReturnValue(output);
    }
}
