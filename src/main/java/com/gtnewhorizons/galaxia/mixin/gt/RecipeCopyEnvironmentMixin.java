package com.gtnewhorizons.galaxia.mixin.gt;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.galaxia.api.RecipeEnvironments;

import gregtech.api.recipe.metadata.IRecipeMetadataStorage;
import gregtech.api.recipe.metadata.RecipeMetadataStorage;
import gregtech.api.util.GTRecipe;

/** Native GT recipe copies discard metadata. Environment restrictions must follow the copied operation. */
@Mixin(value = GTRecipe.class, remap = false)
public abstract class RecipeCopyEnvironmentMixin {

    @Shadow
    @Final
    @Mutable
    private IRecipeMetadataStorage metadataStorage;

    @Inject(method = "<init>(Lgregtech/api/util/GTRecipe;Z)V", at = @At("RETURN"), require = 1)
    private void galaxia$copyRequirement(GTRecipe source, boolean shallow, CallbackInfo ci) {
        var requirement = RecipeEnvironments.of(source);
        if (requirement.tags()
            .isEmpty()) return;
        RecipeMetadataStorage copied = new RecipeMetadataStorage();
        copied.store(RecipeEnvironments.REQUIREMENT, requirement);
        metadataStorage = copied;
    }
}
