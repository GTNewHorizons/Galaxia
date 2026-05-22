package com.gtnewhorizons.galaxia.registry.outpost.module.types;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.TieredModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ProductionModuleHelper;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

public class ModuleChemicalReactor extends TieredModuleComponent implements IParallelModule, IRecipeModule {

    private byte parallel = 1;
    private RecipeConfig recipeConfig;
    final Random random = new Random();
    final Map<RecipeSnapshot, ItemStackWrapper[]> inputWrapperCache = new WeakHashMap<>();
    final Map<RecipeSnapshot, ItemStackWrapper[]> outputWrapperCache = new WeakHashMap<>();

    @Override
    public byte getParallel() {
        return parallel;
    }

    @Override
    public void setParallel(byte parallel) {
        this.parallel = parallel;
    }

    @Override
    public String getRecipeMapName() {
        return "gt.recipe.chemicalreactor";
    }

    @Override
    public RecipeConfig getRecipeConfig() {
        return recipeConfig;
    }

    @Override
    public void setRecipeConfig(RecipeConfig config) {
        this.recipeConfig = config;
    }

    public static void processRecipe(ModuleInstance instance, CelestialAsset outpost) {
        ModuleChemicalReactor m = (ModuleChemicalReactor) instance.component();
        ProductionModuleHelper.execute(instance, outpost, m, m.random, m.inputWrapperCache, m.outputWrapperCache);
    }
}
