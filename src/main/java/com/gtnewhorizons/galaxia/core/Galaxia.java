package com.gtnewhorizons.galaxia.core;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.Tags;
import com.gtnewhorizons.galaxia.core.network.AssetSyncPacket;
import com.gtnewhorizons.galaxia.core.network.DestinationSetPacket;
import com.gtnewhorizons.galaxia.core.network.HazardWarningPacket;
import com.gtnewhorizons.galaxia.core.network.LogisticsSyncPacket;
import com.gtnewhorizons.galaxia.core.network.OxygenSyncPacket;
import com.gtnewhorizons.galaxia.core.network.RocketDestinationSyncPacket;
import com.gtnewhorizons.galaxia.core.network.RocketLaunchPacket;
import com.gtnewhorizons.galaxia.core.network.TeleportRequestPacket;
import com.gtnewhorizons.galaxia.core.network.ToggleRCSPacket;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

@Mod(
    modid = Galaxia.MODID,
    name = Galaxia.NAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "after:gregtech")
public final class Galaxia {

    // spotless:off
    public static final String MODID = "galaxia";
    public static final String NAME = "Galaxia";
    public static final String UNLOCALIZED_PREFIX = MODID + ".";
    public static final String TEXTURE_PREFIX = MODID + ":";
    public static final String REGISTRY_PREFIX = MODID + "_";
    public static final Logger LOG = LogManager.getLogger(MODID);
    public static final SimpleNetworkWrapper GALAXIA_NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    @Mod.Instance(MODID)
    public static Galaxia instance;

    // Caches for baubles inventory indices. Set no earlier than postInit.
    public static int[] oxygenSlots;
    public static int[] oxygenMaskSlots;
    public static int[] shieldSlots;
    public static int[] sporeFilterSlots;
    public static int[] thermalSlot;
    public static int[] witherSlots;
    public static int[] reactionControlSystemSlot;

    @SidedProxy(clientSide = "com.gtnewhorizons.galaxia.core.ClientProxy", serverSide = "com.gtnewhorizons.galaxia.core.CommonProxy")
    public static CommonProxy proxy;
    // spotless:on

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        registerNetwork();
        proxy.init(event);
        FMLInterModComms
            .sendMessage("Waila", "register", "com.gtnewhorizons.galaxia.compat.WailaRocketProvider.register");
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on
    // this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    // spotless:off
    private static void registerNetwork() {
        int id = 0;
        GALAXIA_NETWORK.registerMessage(TeleportRequestPacket.Handler.class, TeleportRequestPacket.class, id++,
                Side.SERVER);
        GALAXIA_NETWORK.registerMessage(OxygenSyncPacket.Handler.class, OxygenSyncPacket.class, id++, Side.CLIENT);
        GALAXIA_NETWORK.registerMessage(HazardWarningPacket.Handler.class, HazardWarningPacket.class, id++,
                Side.CLIENT);
        GALAXIA_NETWORK.registerMessage(DestinationSetPacket.Handler.class, DestinationSetPacket.class, id++,
                Side.SERVER);
        GALAXIA_NETWORK.registerMessage(ToggleRCSPacket.Handler.class, ToggleRCSPacket.class, id++, Side.SERVER);
        GALAXIA_NETWORK.registerMessage(AssetSyncPacket.Handler.class, AssetSyncPacket.class, id++, Side.CLIENT);
        GALAXIA_NETWORK.registerMessage(LogisticsSyncPacket.Handler.class, LogisticsSyncPacket.class, id++,
                Side.CLIENT);
        GALAXIA_NETWORK.registerMessage(RocketLaunchPacket.class, RocketLaunchPacket.class, id++, Side.SERVER);
        GALAXIA_NETWORK.registerMessage(RocketDestinationSyncPacket.Handler.class, RocketDestinationSyncPacket.class,
                id++, Side.SERVER);
    }
    // spotless:on

    public static final CreativeTabs creativeTab = new CreativeTabs(MODID) {

        @Override
        public Item getTabIconItem() {
            return GalaxiaItemList.GALAXIA_LOGO.getItem();
        }
    };

}
