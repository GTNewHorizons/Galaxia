package com.gtnewhorizons.galaxia.registry.outpost.module.types;

import java.util.Random;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.TieredModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ProductionModuleHelper;

public class ModuleElectrolyzer extends TieredModuleComponent implements IParallelModule, IRecipeModule {

    private byte parallel = 1;
    final Random random = new Random();

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
        return "gt.recipe.electrolyzer";
    }

    public static void processRecipe(ModuleInstance instance, CelestialAsset outpost) {
        ModuleElectrolyzer m = (ModuleElectrolyzer) instance.component();
        ProductionModuleHelper.execute(instance, outpost, m.random);
    }
}
