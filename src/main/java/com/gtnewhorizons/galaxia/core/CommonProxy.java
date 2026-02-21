package com.gtnewhorizons.galaxia.core;

import com.gtnewhorizons.galaxia.block.GalaxiaBlocks;
import com.gtnewhorizons.galaxia.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.items.GalaxiaItems;
import com.gtnewhorizons.galaxia.modules.ModuleRegistry;
import com.gtnewhorizons.galaxia.utility.DimensionEventHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        SolarSystemRegistry.registerAll();

        ModuleRegistry.init();

        FMLCommonHandler.instance()
            .bus()
            .register(new DimensionEventHandler());

        GalaxiaBlocks.registerPlanetBlocks();
        GalaxiaBlocks.registerBlocks();
        GalaxiaItems.registerAll();
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {}

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {}
}
