package com.gtnewhorizons.galaxia.compat;

import cpw.mods.fml.common.Loader;

public final class GregTechCompat {

    private GregTechCompat() {}

    public static boolean isGregTechLoaded() {
        return Loader.isModLoaded("gregtech");
    }
}
