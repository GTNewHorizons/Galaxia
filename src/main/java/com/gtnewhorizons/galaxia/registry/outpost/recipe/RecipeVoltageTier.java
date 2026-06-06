package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

public final class RecipeVoltageTier {

    private RecipeVoltageTier() {}

    public static ModuleTier fromEUt(int eut) {
        if (eut <= 0) return ModuleTier.NONE;
        if (eut <= 512) return ModuleTier.HV;
        if (eut <= 2048) return ModuleTier.EV;
        if (eut <= 8192) return ModuleTier.IV;
        return ModuleTier.LuV;
    }
}
