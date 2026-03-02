package com.gtnewhorizons.galaxia.core;

import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenTank.BAUBLE_TYPE_OXYGEN_TANK;
import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemThermalProtection.BAUBLE_TYPE_THERMAL_PROTECTION;

import com.gtnewhorizons.galaxia.handlers.DimensionEventHandler;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.rocketmodules.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.entities.EntityRocket;
import com.gtnewhorizons.galaxia.utility.effects.GalaxiaEffects;

import baubles.api.expanded.BaubleExpandedSlots;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.EntityRegistry;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        SolarSystemRegistry.registerAll();

        FMLCommonHandler.instance()
            .bus()
            .register(new DimensionEventHandler());

        GalaxiaItemList.registerAll();
        GalaxiaBlocksEnum.registerPlanetBlocks();
        GalaxiaBlocksEnum.registerBlocks();
        GalaxiaEffects.init();

        if (Loader.isModLoaded("Baubles|Expanded")) registerBaublesSlots();

        ModuleRegistry.registerAllModules();
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        EntityRegistry.registerModEntity(EntityRocket.class, "RocketEntity", 0, Galaxia.instance, 64, 1, false);
    }

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        Galaxia.oxygenSlots = BaubleExpandedSlots.getIndexesOfAssignedSlotsOfType(BAUBLE_TYPE_OXYGEN_TANK);
        Galaxia.thermalSlot = BaubleExpandedSlots.getIndexesOfAssignedSlotsOfType(BAUBLE_TYPE_THERMAL_PROTECTION);
    }

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {}

    private void registerBaublesSlots() {
        BaubleExpandedSlots.tryRegisterType(BAUBLE_TYPE_OXYGEN_TANK);
        BaubleExpandedSlots.tryAssignSlotOfType(BAUBLE_TYPE_OXYGEN_TANK);
        BaubleExpandedSlots.tryAssignSlotOfType(BAUBLE_TYPE_OXYGEN_TANK);
        BaubleExpandedSlots.tryRegisterType(BAUBLE_TYPE_THERMAL_PROTECTION);
        BaubleExpandedSlots.tryAssignSlotOfType(BAUBLE_TYPE_THERMAL_PROTECTION);
    }
}
