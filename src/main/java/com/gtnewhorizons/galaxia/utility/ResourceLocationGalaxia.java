package com.gtnewhorizons.galaxia.utility;

import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.core.Galaxia;

public class ResourceLocationGalaxia {

    /**
     * static ResourceLocation overload that doesn't require "galaxia" as input
     *
     * @param location file location in galaxia folder
     * @return file location
     */
    public static ResourceLocation LocationGalaxia(String location) {
        return new ResourceLocation(Galaxia.MODID, location);
    }
}
