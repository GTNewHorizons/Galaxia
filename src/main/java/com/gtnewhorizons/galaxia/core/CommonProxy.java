package com.gtnewhorizons.galaxia.core;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.FMLBusRegister;
import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.ForgeBusRegister;
import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.isGregTech5UnofficialNewHorizonsLoaded;
import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenMask.BAUBLE_TYPE_OXYGEN_MASK;
import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenTank.BAUBLE_TYPE_OXYGEN_TANK;
import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemProtectionShield.BAUBLE_TYPE_PROTECTION_SHIELD;
import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemReactionControlSystem.BAUBLE_TYPE_REACTION_CONTROL_SYSTEM;
import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemSporeFilter.BAUBLE_TYPE_SPORE_FILTER;
import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemThermalProtection.BAUBLE_TYPE_THERMAL_PROTECTION;
import static com.gtnewhorizons.galaxia.registry.items.baubles.ItemWitherProtection.BAUBLE_TYPE_WITHER_PROTECTION;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.ForgeChunkManager;

import com.gtnewhorizon.gtnhlib.teams.TeamDataRegistry;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.gui.mui.ItemPickerScreen;
import com.gtnewhorizons.galaxia.compat.GTCompat;
import com.gtnewhorizons.galaxia.compat.ae2.QuantumLinkRules;
import com.gtnewhorizons.galaxia.compat.gt.GalaxiaCasingTextures;
import com.gtnewhorizons.galaxia.compat.gt.GalaxiaGTAttachmentRegistration;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData;
import com.gtnewhorizons.galaxia.core.config.ConfigMain;
import com.gtnewhorizons.galaxia.core.network.ServerTickTaskQueue;
import com.gtnewhorizons.galaxia.core.persistence.FacilityPersistenceManager;
import com.gtnewhorizons.galaxia.gt5.AndroidRecipes;
import com.gtnewhorizons.galaxia.gt5.OrbitalItems;
import com.gtnewhorizons.galaxia.gt5.OrbitalRecipes;
import com.gtnewhorizons.galaxia.gt5.SolarArrayTracking;
import com.gtnewhorizons.galaxia.gt5.SolarEquipment;
import com.gtnewhorizons.galaxia.handlers.CelestialEventHandler;
import com.gtnewhorizons.galaxia.handlers.DimensionEventHandler;
import com.gtnewhorizons.galaxia.handlers.TeamEventHandler;
import com.gtnewhorizons.galaxia.handlers.TetherEventHandler;
import com.gtnewhorizons.galaxia.registry.android.AndroidControl;
import com.gtnewhorizons.galaxia.registry.android.EntityBody;
import com.gtnewhorizons.galaxia.registry.android.TileEntityBodyStation;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.PlanetBlocks;
import com.gtnewhorizons.galaxia.registry.block.special.TileStationItemVault;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.celestial.GalaxiaAtmosphereFluids;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.TileHammerCannon;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.TileHammerTarget;
import com.gtnewhorizons.galaxia.registry.celestial.station.gui.StationGuiFactory;
import com.gtnewhorizons.galaxia.registry.dimension.CelestialDimensionMaterializer;
import com.gtnewhorizons.galaxia.registry.effects.GalaxiaEffects;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocketSeat;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketFlights;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryJumpHandler;

import baubles.api.expanded.BaubleExpandedSlots;
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
        // Dimension ids come from the config, so it is read before anything is registered, on servers too
        ConfigMain.RegisterGalaxiaConfig();
        StationGuiFactory.init();
        CelestialServerRuntime celestialRuntime = CelestialServerRuntime.create();
        TeamEventHandler teamEventHandler = new TeamEventHandler(celestialRuntime);
        AndroidControl androidControl = new AndroidControl();
        ForgeChunkManager.setForcedChunkLoadingCallback(Galaxia.instance, androidControl);
        FMLBusRegister(androidControl);
        ForgeBusRegister(androidControl);
        GalaxiaAtmosphereFluids.init();
        CelestialDimensionMaterializer.registerPlayableDimensions();

        // FML bus registering
        DimensionEventHandler dimensionEffects = new DimensionEventHandler();
        FMLBusRegister(dimensionEffects);
        ForgeBusRegister(dimensionEffects);
        FMLBusRegister(new CelestialEventHandler(celestialRuntime));
        FMLBusRegister(new ServerTickTaskQueue());
        FMLBusRegister(new RocketFlights());
        // Quantum bridges are checked again now and then, after relays come or go
        if (Loader.isModLoaded("appliedenergistics2")) FMLBusRegister(new QuantumLinkRules.Ticker());
        FMLBusRegister(new TetherEventHandler());
        FMLBusRegister(teamEventHandler);

        // Forge bus registering
        ForgeBusRegister(new FacilityPersistenceManager(celestialRuntime));
        ForgeBusRegister(teamEventHandler);
        ForgeBusRegister(new GalaxiaPlayerProperties.PlayerEventHandler());
        ForgeBusRegister(new GantryJumpHandler());

        // GTNH Teams custom data
        TeamDataRegistry.register(GalaxiaTeamData.ID, GalaxiaTeamData::new);

        // Registration
        GalaxiaItemList.registerAll();
        OrbitalItems.register();
        SolarEquipment.registerPanel();
        FMLBusRegister(SolarArrayTracking.EVENTS);
        ForgeBusRegister(SolarArrayTracking.EVENTS);
        GalaxiaBlocksEnum.registerBlocks();
        PlanetBlocks.init();
        GalaxiaEffects.init();

        // Facility setup
        FacilityModuleKind.setGt5Available(isGregTech5UnofficialNewHorizonsLoaded());
        FacilityModuleRegistry.init();

        if (Loader.isModLoaded("Baubles|Expanded")) registerBaublesSlots();

        RocketPartRegistry.instance()
            .registerAll();
    }

    // load "Do your mod setup. Build whatever data structures you care about.
    // Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        NetworkManager.registerPackets();
        EntityRegistry.registerModEntity(EntityRocket.class, "RocketEntity", 0, Galaxia.instance, 64, 1, false);
        EntityRegistry.registerModEntity(EntityRocketSeat.class, "RocketSeat", 1, Galaxia.instance, 64, 1, false);
        EntityRegistry.registerModEntity(EntityBody.class, "PhysicalBody", 2, Galaxia.instance, 64, 2, true);

        registerAttachments();

        // Why Gui code on server? idk ask mui2
        ItemPickerScreen.FACTORY.init();
        TileEntityBodyStation.registerGuiFactory();
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
        Galaxia.rcsSlot = BaubleExpandedSlots.getIndexesOfAssignedSlotsOfType(BAUBLE_TYPE_REACTION_CONTROL_SYSTEM);
        CelestialRegistry.freezeAndBake();
        validateGtCelestialOrePools();
        if (isGregTech5UnofficialNewHorizonsLoaded()) {
            AndroidRecipes.init();
            com.gtnewhorizons.galaxia.gt5.LunarRecipes.register();
            OrbitalRecipes.init();
        }
    }

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {}

    private void registerAttachments() {
        if (isGregTech5UnofficialNewHorizonsLoaded()) {
            GalaxiaGTAttachmentRegistration.init();
            SolarEquipment.registerCollector();
            GalaxiaCasingTextures.register();
        }

        StationAttachmentRegistry.register(TileHammerTarget.class, TileHammerTarget.HANDLER);
        StationAttachmentRegistry.register(TileHammerCannon.class, TileHammerCannon.HANDLER);
        StationAttachmentRegistry.register(TileStationItemVault.class, TileStationItemVault.HANDLER);
    }

    private void validateGtCelestialOrePools() {
        if (!isGregTech5UnofficialNewHorizonsLoaded()) return;

        int bodyCount = 0;
        int stackCount = 0;
        int incompleteDeposits = 0;
        for (CelestialObject body : GalaxiaCelestialAPI.getAll()) {
            var properties = body.properties();
            if (!properties.hasGtOreDepositIds()) continue;

            bodyCount++;
            for (String depositId : properties.gtOreDepositIds()) {
                int expected = depositId.startsWith("ore.small.") ? 1 : 4;
                int resolved = GTCompat.getGtOreDepositStacks(depositId)
                    .size();
                if (resolved != expected) {
                    incompleteDeposits++;
                    Galaxia.LOG.error(
                        "[GT_ORE_AUDIT] {} deposit {} resolved {} of {} ore stacks",
                        body.key(),
                        depositId,
                        resolved,
                        expected);
                }
            }
            List<ItemStack> gtOres = properties.getResolvedGtOreStacks();
            if (gtOres.isEmpty()) {
                Galaxia.LOG.error(
                    "[GT_ORE_AUDIT] {} declares GT ore deposits {} but resolved no GT ore stacks",
                    body.key(),
                    properties.gtOreDepositIds());
                continue;
            }

            stackCount += gtOres.size();
            Galaxia.LOG.info(
                "[GT_ORE_AUDIT] {} resolved {} GT ore stacks from {}",
                body.key(),
                gtOres.size(),
                properties.gtOreDepositIds());
        }
        Galaxia.LOG.info(
            "[GT_ORE_AUDIT] checked {} celestial bodies, {} resolved stacks, {} incomplete deposits",
            bodyCount,
            stackCount,
            incompleteDeposits);
    }

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

        BaubleExpandedSlots.tryRegisterType(BAUBLE_TYPE_REACTION_CONTROL_SYSTEM);
        BaubleExpandedSlots.tryAssignSlotOfType(BAUBLE_TYPE_REACTION_CONTROL_SYSTEM);
    }

}
