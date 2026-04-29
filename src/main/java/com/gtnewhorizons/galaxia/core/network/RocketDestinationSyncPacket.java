package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class RocketDestinationSyncPacket implements IMessage {

    private int rocketEntityId;
    private int destinationDim;

    public RocketDestinationSyncPacket() {}

    public RocketDestinationSyncPacket(int rocketEntityId, int destinationDim) {
        this.rocketEntityId = rocketEntityId;
        this.destinationDim = destinationDim;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.rocketEntityId = buf.readInt();
        this.destinationDim = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.rocketEntityId);
        buf.writeInt(this.destinationDim);
    }

    public static class Handler implements IMessageHandler<RocketDestinationSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(RocketDestinationSyncPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;

            ServerTickTaskQueue.schedule(() -> {
                Entity entity = player.worldObj.getEntityByID(message.rocketEntityId);
                if (entity instanceof EntityRocket rocket) {
                    rocket.setDestination(message.destinationDim);
                }

            });
            return null;
        }
    }
}
