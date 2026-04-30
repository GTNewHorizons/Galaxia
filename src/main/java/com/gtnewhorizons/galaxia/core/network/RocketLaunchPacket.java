package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket.Phase;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class RocketLaunchPacket implements IMessage, IMessageHandler<RocketLaunchPacket, IMessage> {

    private int rocketId;

    public RocketLaunchPacket() {}

    public RocketLaunchPacket(int rocketId) {
        this.rocketId = rocketId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.rocketId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.rocketId);
    }

    @Override
    public IMessage onMessage(RocketLaunchPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        if (player != null && player.worldObj != null) {
            Entity entity = player.worldObj.getEntityByID(message.rocketId);

            if (entity instanceof EntityRocket rocket) {
                if (rocket.riddenByEntity == player) {
                    rocket.setPhase(Phase.LAUNCHING);
                }
            }

        }
        return null;
    }
}
