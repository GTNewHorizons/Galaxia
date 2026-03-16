package com.gtnewhorizons.galaxia.core;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizons.galaxia.core.config.ConfigMain;
import com.gtnewhorizons.galaxia.core.nei.GalaxiaMultiblockHandler;
import com.gtnewhorizons.galaxia.handlers.GalaxiaOverlayHandler;
import com.gtnewhorizons.galaxia.rocketmodules.client.render.MonorailRenderer;
import com.gtnewhorizons.galaxia.rocketmodules.client.render.RocketRenderer;
import com.gtnewhorizons.galaxia.rocketmodules.client.render.SiloRenderer;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

import codechicken.nei.api.API;
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

        final SiloRenderer siloRenderer = new SiloRenderer();
        final MonorailRenderer monorailRenderer = new MonorailRenderer();

        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySilo.class, new TileEntitySpecialRenderer() {

            @Override
            public void renderTileEntityAt(net.minecraft.tileentity.TileEntity te, double x, double y, double z,
                float partialTick) {
                siloRenderer.renderTileEntityAt(te, x, y, z, partialTick);
                monorailRenderer.renderTileEntityAt(te, x, y, z, partialTick);
            }
        });
        RenderingRegistry.registerEntityRenderingHandler(EntityRocket.class, new RocketRenderer());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);

        GalaxiaMultiblockHandler.register(new TileEntitySilo());

        GalaxiaMultiblockHandler handler = new GalaxiaMultiblockHandler();
        API.registerRecipeHandler(handler);
        API.registerUsageHandler(handler);
        API.addRecipeCatalyst(new ItemStack(StructureLibAPI.getDefaultHologramItem()), handler.getOverlayIdentifier());
    }

    @Override
    public void serverStarting(FMLServerStartingEvent event) {
        super.serverStarting(event);
    }
}
