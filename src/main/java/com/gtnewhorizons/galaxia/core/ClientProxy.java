package com.gtnewhorizons.galaxia.core;

import com.gtnewhorizons.galaxia.client.render.RocketWorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizons.galaxia.core.config.ConfigOverlay;
import com.gtnewhorizons.galaxia.handlers.GalaxiaOverlayHandler;
import com.gtnewhorizons.galaxia.modules.client.render.ModuleTESR;
import com.gtnewhorizons.galaxia.registry.block.tileentities.TileEntityModuleController;
import com.gtnewhorizons.galaxia.registry.entity.rocket.RocketEntity;

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
        try {
            ConfigurationManager.registerConfig(ConfigOverlay.class);
        } catch (ConfigException e) {
            throw new RuntimeException("Failed to register Galaxia config", e);
        }
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityModuleController.class, new ModuleTESR());
        MinecraftForge.EVENT_BUS.register(new GalaxiaOverlayHandler());
        RocketWorldRenderer.register();
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
