package com.gtnewhorizons.galaxia.latemixin.gregtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.gtnewhorizons.galaxia.compat.gt.OrbitMachineRules;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchMuffler;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchMufflerAdvanced;

/** Keeps native muffler venting and filtering while EBF exhaust disperses into vacuum without pollution. */
@Mixin(value = { MTEHatchMuffler.class, MTEHatchMufflerAdvanced.class }, remap = false)
public abstract class MTEHatchMufflerVacuumMixin {

    @WrapWithCondition(
        method = "polluteEnvironment",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/pollution/Pollution;addPollution(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;I)V"),
        require = 1)
    private boolean galaxia$emitPollutionInAtmosphere(IGregTechTileEntity hatch, int pollution,
        @Local(argsOnly = true) MetaTileEntity controller) {
        return !OrbitMachineRules.ventsWithoutPollution(controller);
    }
}
