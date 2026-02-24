package com.gtnewhorizons.galaxia.client.gui;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.items.special.ItemHabitatBuilder;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketSetModule implements IMessage {

    private String moduleId;

    public PacketSetModule() {}

    public PacketSetModule(String id) {
        this.moduleId = id;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, moduleId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        moduleId = ByteBufUtils.readUTF8String(buf);
    }

    public static class Handler implements IMessageHandler<PacketSetModule, IMessage> {

        @Override
        public IMessage onMessage(final PacketSetModule message, final MessageContext ctx) {

            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ItemStack stack = player.getHeldItem();

            if (stack != null) {
                ItemHabitatBuilder.setSelectedModule(stack, message.moduleId);
            }

            return null;
        }

    }
}
