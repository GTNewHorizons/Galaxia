package com.gtnewhorizons.galaxia.core.nei;

import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.event.FMLInterModComms;

public class IMCForNEI {

    public static void IMCSender() {
        registerHandlerInfo(
            GalaxiaMultiblockHandler.class.getName(),
            "structurelib:item.structurelib.constructableTrigger",
            166);
    }

    private static void registerHandlerInfo(String name, String stack, int height) {
        NBTTagCompound handlerInfo = new NBTTagCompound();
        handlerInfo.setString("handler", name);
        handlerInfo.setString("modName", "Galaxia");
        handlerInfo.setString("modId", "galaxia");
        handlerInfo.setBoolean("modRequired", true);
        handlerInfo.setString("itemName", stack);
        handlerInfo.setInteger("handlerHeight", height);
        handlerInfo.setInteger("maxRecipesPerPage", 1);
        FMLInterModComms.sendMessage("NotEnoughItems", "registerHandlerInfo", handlerInfo);
    }

    private static void registerCatalystInfo(String handlerName, String stack) {
        NBTTagCompound catalystInfo = new NBTTagCompound();
        catalystInfo.setString("handlerID", handlerName);
        catalystInfo.setString("itemName", stack);
        catalystInfo.setInteger("priority", 0);
        FMLInterModComms.sendMessage("NotEnoughItems", "registerCatalystInfo", catalystInfo);
    }
}
