package com.cleanroommc.galaxia;

import com.cleanroommc.galaxia.config.GalaxiaConfig;
import com.cleanroommc.galaxia.world.WorldProviderCalx;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraftforge.common.DimensionManager;

public class DimensionRegistry {

    public static void registerDimensions() {
        int providerType = GalaxiaConfig.CALX_DIM_ID;
        int dimensionId   = GalaxiaConfig.CALX_DIM_ID;
        DimensionManager.registerProviderType(providerType, WorldProviderCalx.class, true);

        if (!DimensionManager.isDimensionRegistered(dimensionId)) {
            DimensionManager.registerDimension(dimensionId, providerType);
        } else {
            System.out.println("[Galaxia] Dimension ID " + dimensionId + " taken");
        }
    }
}
