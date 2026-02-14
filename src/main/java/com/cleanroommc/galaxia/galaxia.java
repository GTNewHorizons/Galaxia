package com.cleanroommc.galaxia;

import com.cleanroommc.galaxia.config.GalaxiaConfig;
import com.cleanroommc.galaxia.items.ItemCalxTeleporter;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = galaxia.MODID, version = Tags.VERSION, name = "Galaxia", acceptedMinecraftVersions = "[1.7.10]")
public class galaxia {

    public static Item calxTeleporter;
    public static CreativeTabs creativeTab = new CreativeTabs("galaxia") {
        @Override public Item getTabIconItem() { return Item.getItemById(264); }
    };

    public static final String MODID = "galaxia";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.cleanroommc.galaxia.ClientProxy", serverSide = "com.cleanroommc.galaxia.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        GalaxiaConfig.init(event.getSuggestedConfigurationFile());
        DimensionRegistry.registerDimensions();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        calxTeleporter = new ItemCalxTeleporter().setUnlocalizedName("calxTeleporter")
            .setCreativeTab(creativeTab).setMaxStackSize(16);
        GameRegistry.registerItem(calxTeleporter, "calxTeleporter");

        GameRegistry.addRecipe(new ItemStack(calxTeleporter),
            "III", "IEI", "III",
            'I', Items.iron_ingot,
            'E', Items.ender_pearl);
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
