package com.gtnewhorizons.galaxia.core;

import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizons.galaxia.core.config.ConfigMain;
import com.gtnewhorizons.galaxia.handlers.GalaxiaOverlayHandler;
import com.gtnewhorizons.galaxia.handlers.RocketCameraHandler;
import com.gtnewhorizons.galaxia.rocketmodules.client.render.RocketRenderer;
import com.gtnewhorizons.galaxia.rocketmodules.client.render.SiloRenderer;
import com.gtnewhorizons.galaxia.rocketmodules.entities.EntityRocket;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ConfigMain.RegisterGalaxiaConfig();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new GalaxiaOverlayHandler());

        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySilo.class, new SiloRenderer());
        RenderingRegistry.registerEntityRenderingHandler(EntityRocket.class, new RocketRenderer());
        RocketCameraHandler.register();
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }

    @Override
    public void serverStarting(FMLServerStartingEvent event) {
        super.serverStarting(event);
    }
}
