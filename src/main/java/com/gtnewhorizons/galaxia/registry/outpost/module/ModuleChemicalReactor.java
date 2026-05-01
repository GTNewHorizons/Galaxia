package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;

import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;

public class ModuleChemicalReactor implements ModuleComponent, IParallelModule, IRecipeModule {

    private byte parallel = 1;
    private RecipeConfig recipeConfig;
    final Random random = new Random();
    final Map<GTRecipe, ItemStackWrapper[]> inputWrapperCache = new WeakHashMap<>();
    final Map<GTRecipe, ItemStackWrapper[]> outputWrapperCache = new WeakHashMap<>();

    @Override
    public byte getParallel() {
        return parallel;
    }

    @Override
    public void setParallel(byte parallel) {
        this.parallel = parallel;
    }

    @Override
    public gregtech.api.recipe.RecipeMap<?> getRecipeMap() {
        return RecipeMaps.chemicalReactorRecipes;
    }

    @Override
    public RecipeConfig getRecipeConfig() {
        return recipeConfig;
    }

    @Override
    public void setRecipeConfig(RecipeConfig config) {
        this.recipeConfig = config;
    }

    public static void processRecipe(ModuleInstance instance, AutomatedFacility outpost) {
        ModuleChemicalReactor m = (ModuleChemicalReactor) instance.component();
        ProductionModuleHelper.execute(instance, outpost, m, m.random, m.inputWrapperCache, m.outputWrapperCache);
    }
}
