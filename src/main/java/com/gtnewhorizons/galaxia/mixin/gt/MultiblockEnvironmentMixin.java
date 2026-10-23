package com.gtnewhorizons.galaxia.mixin.gt;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.galaxia.api.RecipeEnvironments;
import com.gtnewhorizons.galaxia.api.RecipeEnvironments.Requirement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class MultiblockEnvironmentMixin implements RecipeEnvironments.Machine {

    @Unique
    private Requirement galaxia$requirement = RecipeEnvironments.NONE;

    @Override
    public void galaxia$setRequirement(Requirement requirement) {
        galaxia$requirement = requirement;
    }

    @WrapOperation(
        method = "runMachine",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/metatileentity/implementations/MTEMultiBlockBase;onRunningTick(Lnet/minecraft/item/ItemStack;)Z"),
        require = 1)
    private boolean galaxia$useNativePowerLoss(MTEMultiBlockBase machine, ItemStack stack,
        Operation<Boolean> original) {
        if (!RecipeEnvironments.matches(galaxia$requirement, machine)) {
            machine.stopMachine(ShutDownReasonRegistry.POWER_LOSS);
            return false;
        }
        return original.call(machine, stack);
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"), require = 1)
    private void galaxia$saveRequirement(NBTTagCompound nbt, CallbackInfo ci) {
        galaxia$requirement.writeToNBT(nbt);
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"), require = 1)
    private void galaxia$loadRequirement(NBTTagCompound nbt, CallbackInfo ci) {
        galaxia$requirement = Requirement.readFromNBT(nbt);
    }
}
