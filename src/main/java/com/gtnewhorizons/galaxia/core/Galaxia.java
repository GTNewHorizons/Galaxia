package com.gtnewhorizons.galaxia.core;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.Tags;
import com.gtnewhorizons.galaxia.client.gui.PacketSetModule;
import com.gtnewhorizons.galaxia.core.network.TeleportRequestPacket;
import com.gtnewhorizons.galaxia.items.GalaxiaItemList;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

@Mod(modid = Galaxia.MODID, name = Galaxia.NAME, version = Tags.VERSION, acceptedMinecraftVersions = "[1.7.10]")
public final class Galaxia {

    // spotless:off
    public static final String MODID = "galaxia";
    public static final String NAME = "Galaxia";
    public static final String UNLOCALIZED_PREFIX = MODID + ".";
    public static final Logger LOG = LogManager.getLogger(MODID);
    public static final SimpleNetworkWrapper GALAXIA_NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    @SidedProxy(
        clientSide = "com.gtnewhorizons.galaxia.core.ClientProxy",
        serverSide = "com.gtnewhorizons.galaxia.core.CommonProxy"
    )
    public static CommonProxy proxy;
    //spotless:on

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        registerNetwork();
        proxy.init(event);
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
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
        GALAXIA_NETWORK.registerMessage(TeleportRequestPacket.Handler.class, TeleportRequestPacket.class, id++, Side.SERVER);
        GALAXIA_NETWORK.registerMessage(PacketSetModule.Handler.class, PacketSetModule.class, id++, Side.SERVER);
    }
    // spotless:on

    public static final CreativeTabs creativeTab = new CreativeTabs(MODID) {

        @Override
        public Item getTabIconItem() {
            return GalaxiaItemList.GALAXIA_LOGO.getItem();
        }
    };
}
