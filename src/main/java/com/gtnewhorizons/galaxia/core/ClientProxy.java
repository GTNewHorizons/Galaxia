package com.gtnewhorizons.galaxia.core;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.FMLBusRegister;
import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.ForgeBusRegister;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.MinecraftForgeClient;

import com.gtnewhorizons.galaxia.client.GalaxiaKeyBinds;
import com.gtnewhorizons.galaxia.client.render.rockets.GantryItemRenderer;
import com.gtnewhorizons.galaxia.client.render.rockets.GantryPlacementPreviewHandler;
import com.gtnewhorizons.galaxia.client.render.rockets.GantryRenderer;
import com.gtnewhorizons.galaxia.client.render.rockets.RocketRenderer;
import com.gtnewhorizons.galaxia.client.render.rockets.RocketSchematicItemRenderer;
import com.gtnewhorizons.galaxia.client.render.rockets.RocketTrophyRenderer;
import com.gtnewhorizons.galaxia.client.render.rockets.SiloRenderer;
import com.gtnewhorizons.galaxia.client.render.sky.GalaxiaSkyBootstrap;
import com.gtnewhorizons.galaxia.core.config.ConfigMain;
import com.gtnewhorizons.galaxia.core.nei.GalaxiaMultiblockHandler;
import com.gtnewhorizons.galaxia.core.nei.IMCForNEI;
import com.gtnewhorizons.galaxia.handlers.GalaxiaOverlayHandler;
import com.gtnewhorizons.galaxia.handlers.KeyHandler;
import com.gtnewhorizons.galaxia.handlers.SkyUpdateHandler;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileEntityAirlock;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.TileHammerTarget;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.items.special.ItemKineticTether;
import com.gtnewhorizons.galaxia.registry.items.tether.TetherRenderer;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityRocketTrophy;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantry;

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
        ForgeBusRegister(new ItemKineticTether.ClientEventHandler());
        FMLBusRegister(new KeyHandler());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // GUI init
        GalaxiaSkyBootstrap.clientInit();

        // StructureLib hook
        IMCForNEI.IMCSender();

        // Handlers
        ForgeBusRegister(new GalaxiaOverlayHandler());
        ForgeBusRegister(new GantryPlacementPreviewHandler());
        FMLBusRegister(new SkyUpdateHandler());

        // Keys
        GalaxiaKeyBinds.init();

        // TESR
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySilo.class, new SiloRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityRocketTrophy.class, new RocketTrophyRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityGantry.class, new GantryRenderer());

        // Entities
        RenderingRegistry.registerEntityRenderingHandler(EntityRocket.class, new RocketRenderer());

        // Items
        MinecraftForgeClient
            .registerItemRenderer(Item.getItemFromBlock(GalaxiaBlocksEnum.GANTRY.get()), new GantryItemRenderer());
        MinecraftForgeClient
            .registerItemRenderer(GalaxiaItemList.ITEM_ROCKET_SCHEMATIC.getItem(), new RocketSchematicItemRenderer());
        ForgeBusRegister(new TetherRenderer());

    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);

        // Hide Logo Item
        API.hideItem(new ItemStack(GalaxiaItemList.GALAXIA_LOGO.getItem()));

        // StructureLib registering
        GalaxiaMultiblockHandler.register(new TileEntitySilo());
        GalaxiaMultiblockHandler.register(new TileEntityModuleAssembler());
        GalaxiaMultiblockHandler.register(new TileEntityAirlock());
        GalaxiaMultiblockHandler.register(new TileHammerTarget());

        GalaxiaMultiblockHandler handler = new GalaxiaMultiblockHandler();
        API.registerRecipeHandler(handler);
        API.registerUsageHandler(handler);
    }

    @Override
    public void serverStarting(FMLServerStartingEvent event) {
        super.serverStarting(event);
    }

}
