package com.gtnewhorizons.galaxia.core;

import com.gtnewhorizons.galaxia.handlers.DimensionEventHandler;
import com.gtnewhorizons.galaxia.handlers.GuiHandler;
import com.gtnewhorizons.galaxia.modules.ModuleRegistry;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.registry.entity.rocket.RocketEntity;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.EntityRegistry;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        SolarSystemRegistry.registerAll();

        ModuleRegistry.init();

        FMLCommonHandler.instance()
            .bus()
            .register(new DimensionEventHandler());

        GalaxiaItemList.registerAll();
        GalaxiaBlocksEnum.registerPlanetBlocks();
        GalaxiaBlocksEnum.registerBlocks();

        EntityRegistry.registerModEntity(
            RocketEntity.class, "GalaxiaRocket",
            123, Galaxia.instance,
            256, 5, true
        );
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(Galaxia.instance, new GuiHandler());
    }

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {}
}
