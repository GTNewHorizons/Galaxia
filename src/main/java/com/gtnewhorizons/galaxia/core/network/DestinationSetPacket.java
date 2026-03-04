
package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class DestinationSetPacket implements IMessage {

    private int x, y, z;
    private int destination;

    public DestinationSetPacket() {}

    public DestinationSetPacket(int x, int y, int z, int destination) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.destination = destination;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(destination);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        destination = buf.readInt();
    }

    public static class Handler implements IMessageHandler<DestinationSetPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(DestinationSetPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            TileEntitySilo te = (TileEntitySilo) player.worldObj.getTileEntity(message.x, message.y, message.z);
            if (te != null) {
                te.setDesination(message.destination);
            }

            return null;
        }
    }
}
