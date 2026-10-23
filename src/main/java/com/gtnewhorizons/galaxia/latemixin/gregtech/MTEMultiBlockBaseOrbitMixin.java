package com.gtnewhorizons.galaxia.latemixin.gregtech;

import javax.annotation.Nonnull;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.galaxia.compat.gt.OrbitMachineRules;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;

/**
 * Applies orbital exhaust admission rules, including EBF venting into vacuum.
 */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class MTEMultiBlockBaseOrbitMixin {

    @Shadow
    protected @Nonnull CheckRecipeResult checkRecipeResult;

    @Inject(method = "checkRecipe()Z", at = @At("HEAD"), cancellable = true)
    private void galaxia$noExhaustInOrbit(CallbackInfoReturnable<Boolean> cir) {
        if (!OrbitMachineRules.isExhaustBlocked((MTEMultiBlockBase) (Object) this)) return;
        checkRecipeResult = OrbitMachineRules.NO_EXHAUST_IN_ORBIT;
        cir.setReturnValue(false);
    }
}
