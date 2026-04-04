package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.utility.GalaxiaAPI;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class ToggleRCSPacket implements IMessage {

    private boolean enabled;

    public ToggleRCSPacket() {}

    public ToggleRCSPacket(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.enabled = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.enabled);
    }

    public static class Handler implements IMessageHandler<ToggleRCSPacket, IMessage> {

        @Override
        public IMessage onMessage(ToggleRCSPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;

            ServerTickTaskQueue.schedule(() -> { GalaxiaAPI.setZeroGMovement(player, message.enabled); });

            return null;
        }
    }
}
