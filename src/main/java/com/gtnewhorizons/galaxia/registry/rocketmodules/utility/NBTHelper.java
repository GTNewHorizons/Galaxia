package com.gtnewhorizons.galaxia.registry.rocketmodules.utility;

import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class NBTHelper {

    public static void writeNBT(ByteBuf buf, NBTTagCompound tag) {
        ByteBufUtils.writeTag(buf, tag);
    }

    public static NBTTagCompound readNBT(ByteBuf buf) {
        return ByteBufUtils.readTag(buf);
    }
}
