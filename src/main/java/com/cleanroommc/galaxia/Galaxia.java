package com.cleanroommc.galaxia;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cleanroommc.galaxia.dimension.SolarSystemRegistry;
import com.cleanroommc.galaxia.items.GalaxiaItems;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

@Mod(modid = Galaxia.MODID, version = Tags.VERSION, name = "Galaxia", acceptedMinecraftVersions = "[1.7.10]")
public class Galaxia {

    public static final SimpleNetworkWrapper channel = NetworkRegistry.INSTANCE.newSimpleChannel("galaxia");
    public static CreativeTabs creativeTab = new CreativeTabs("galaxia") {

        @Override
        public Item getTabIconItem() {
            return Item.getItemById(264);
        }
    };

    public static final String MODID = "galaxia";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.cleanroommc.galaxia.ClientProxy", serverSide = "com.cleanroommc.galaxia.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        SolarSystemRegistry.registerAll();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        int packetId = 0;
        channel
            .registerMessage(TeleportRequestPacket.Handler.class, TeleportRequestPacket.class, packetId++, Side.SERVER);
        GalaxiaItems.registerAll();
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
}
