package com.gtnewhorizons.galaxia.core;

import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenMask.BAUBLE_TYPE_OXYGEN_MASK;
import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenTank.BAUBLE_TYPE_OXYGEN_TANK;
import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemProtectionShield.BAUBLE_TYPE_PROTECTION_SHIELD;
import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemSporeFilter.BAUBLE_TYPE_SPORE_FILTER;
import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemThermalProtection.BAUBLE_TYPE_THERMAL_PROTECTION;
import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemWitherProtection.BAUBLE_TYPE_WITHER_PROTECTION;

import com.gtnewhorizons.galaxia.handlers.DimensionEventHandler;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksMiscEnum;
import com.gtnewhorizons.galaxia.registry.block.planet.PlanetBlocks;
import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.rocketmodules.link.LinkRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;
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

    // preInit "Run before anything else. Read your config, create blocks, items,
    // etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        SolarSystemRegistry.registerAll();

        FMLCommonHandler.instance()
            .bus()
            .register(new DimensionEventHandler());

        GalaxiaItemList.registerAll();
        GalaxiaBlocksEnum.registerBlocks();
        GalaxiaBlocksMiscEnum.registerBlocksMisc();
        PlanetBlocks.init();
        GalaxiaEffects.init();
        registerLinks();

        if (Loader.isModLoaded("Baubles|Expanded")) registerBaublesSlots();

        ModuleRegistry.registerAllModules();
    }

    // load "Do your mod setup. Build whatever data structures you care about.
    // Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        EntityRegistry.registerModEntity(EntityRocket.class, "RocketEntity", 0, Galaxia.instance, 64, 1, false);
    }

    // postInit "Handle interaction with other mods, complete your setup based on
    // this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        Galaxia.oxygenSlots = BaubleExpandedSlots.getIndexesOfAssignedSlotsOfType(BAUBLE_TYPE_OXYGEN_TANK);
        Galaxia.shieldSlots = BaubleExpandedSlots.getIndexesOfAssignedSlotsOfType(BAUBLE_TYPE_PROTECTION_SHIELD);
        Galaxia.oxygenMaskSlots = BaubleExpandedSlots.getIndexesOfAssignedSlotsOfType(BAUBLE_TYPE_OXYGEN_MASK);
        Galaxia.sporeFilterSlots = BaubleExpandedSlots.getIndexesOfAssignedSlotsOfType(BAUBLE_TYPE_SPORE_FILTER);
        Galaxia.thermalSlot = BaubleExpandedSlots.getIndexesOfAssignedSlotsOfType(BAUBLE_TYPE_THERMAL_PROTECTION);
        Galaxia.witherSlots = BaubleExpandedSlots.getIndexesOfAssignedSlotsOfType(BAUBLE_TYPE_WITHER_PROTECTION);
    }

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {}

    private void registerBaublesSlots() {
        BaubleExpandedSlots.tryRegisterType(BAUBLE_TYPE_OXYGEN_TANK);
        BaubleExpandedSlots.tryAssignSlotOfType(BAUBLE_TYPE_OXYGEN_TANK);
        BaubleExpandedSlots.tryAssignSlotOfType(BAUBLE_TYPE_OXYGEN_TANK);

        BaubleExpandedSlots.tryRegisterType(BAUBLE_TYPE_PROTECTION_SHIELD);
        BaubleExpandedSlots.tryAssignSlotOfType(BAUBLE_TYPE_PROTECTION_SHIELD);
        BaubleExpandedSlots.tryAssignSlotOfType(BAUBLE_TYPE_PROTECTION_SHIELD);

        BaubleExpandedSlots.tryRegisterType(BAUBLE_TYPE_OXYGEN_MASK);
        BaubleExpandedSlots.tryAssignSlotOfType(BAUBLE_TYPE_OXYGEN_MASK);

        BaubleExpandedSlots.tryRegisterType(BAUBLE_TYPE_SPORE_FILTER);
        BaubleExpandedSlots.tryAssignSlotOfType(BAUBLE_TYPE_SPORE_FILTER);

        BaubleExpandedSlots.tryRegisterType(BAUBLE_TYPE_THERMAL_PROTECTION);
        BaubleExpandedSlots.tryAssignSlotOfType(BAUBLE_TYPE_THERMAL_PROTECTION);

        BaubleExpandedSlots.tryRegisterType(BAUBLE_TYPE_WITHER_PROTECTION);
        BaubleExpandedSlots.tryAssignSlotOfType(BAUBLE_TYPE_WITHER_PROTECTION);

    }

    private void registerLinks() {
        LinkRegistry.register(TileEntityModuleAssembler.class, TileEntitySilo.class);
    }
}
