package com.gtnewhorizons.galaxia.mixin.gt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.gtnewhorizons.galaxia.api.RecipeEnvironments;
import com.gtnewhorizons.galaxia.api.RecipeEnvironments.Requirement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import gregtech.api.interfaces.tileentity.IVoidable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;

@Mixin(value = ProcessingLogic.class, remap = false)
public abstract class ProcessingEnvironmentMixin {

    @Shadow
    protected IVoidable machine;

    @WrapOperation(
        method = "validateAndCalculateRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/logic/ProcessingLogic;validateRecipe(Lgregtech/api/util/GTRecipe;)Lgregtech/api/recipe/check/CheckRecipeResult;"),
        require = 1)
    private CheckRecipeResult galaxia$checkBeforeConsumption(ProcessingLogic logic, GTRecipe recipe,
        Operation<CheckRecipeResult> original) {
        Requirement requirement = RecipeEnvironments.of(recipe);
        MetaTileEntity controller = machine instanceof MetaTileEntity tile ? tile : null;
        if (!RecipeEnvironments.matches(requirement, controller)) {
            return SimpleCheckRecipeResult.ofFailure("galaxia_environment");
        }
        return original.call(logic, recipe);
    }

    @WrapOperation(
        method = "validateAndCalculateRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/logic/ProcessingLogic;applyRecipe(Lgregtech/api/util/GTRecipe;Lgregtech/api/util/ParallelHelper;Lgregtech/api/util/OverclockCalculator;Lgregtech/api/recipe/check/CheckRecipeResult;)Lgregtech/api/recipe/check/CheckRecipeResult;"),
        require = 1)
    private CheckRecipeResult galaxia$rememberAcceptedRecipe(ProcessingLogic logic, GTRecipe recipe,
        ParallelHelper helper, OverclockCalculator calculator, CheckRecipeResult validation,
        Operation<CheckRecipeResult> original) {
        CheckRecipeResult result = original.call(logic, recipe, helper, calculator, validation);
        if (result.wasSuccessful() && machine instanceof RecipeEnvironments.Machine state) {
            state.galaxia$setRequirement(RecipeEnvironments.of(recipe));
        }
        return result;
    }
}
