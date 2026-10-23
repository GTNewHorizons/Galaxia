package com.gtnewhorizons.galaxia.mixin.gt;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.galaxia.api.RecipeEnvironments;
import com.gtnewhorizons.galaxia.api.RecipeEnvironments.Requirement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.util.GTRecipe;

@Mixin(value = MTEBasicMachine.class, remap = false)
public abstract class BasicMachineEnvironmentMixin implements RecipeEnvironments.Machine {

    @Unique
    private Requirement galaxia$requirement = RecipeEnvironments.NONE;

    @Override
    public void galaxia$setRequirement(Requirement requirement) {
        galaxia$requirement = requirement;
    }

    @WrapOperation(
        method = "checkRecipe(Z)I",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/util/GTRecipe;isRecipeInputEqual(Z[Lnet/minecraftforge/fluids/FluidStack;[Lnet/minecraft/item/ItemStack;)Z"),
        require = 1)
    private boolean galaxia$checkBeforeConsumption(GTRecipe recipe, boolean consume, FluidStack[] fluids,
        ItemStack[] items, Operation<Boolean> original) {
        Requirement requirement = RecipeEnvironments.of(recipe);
        if (!RecipeEnvironments.matches(requirement, (MTEBasicMachine) (Object) this)) return false;
        boolean accepted = original.call(recipe, consume, fluids, items);
        if (accepted) galaxia$requirement = requirement;
        return accepted;
    }

    @WrapOperation(
        method = "onPostTick",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/metatileentity/implementations/MTEBasicMachine;drainEnergyForProcess(J)Z"),
        require = 1)
    private boolean galaxia$useNativeEnergyFailure(MTEBasicMachine machine, long eut, Operation<Boolean> original) {
        return RecipeEnvironments.matches(galaxia$requirement, machine) && original.call(machine, eut);
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
