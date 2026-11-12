package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionTeleport;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Class used to create packets to the server to request entity teleportation
 */
public class TeleportRequestPacket implements IMessage {

    private int dim;
    private double x, y, z;

    public TeleportRequestPacket() {}

    public TeleportRequestPacket(int dim, double x, double y, double z) {
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Writes the dimension and coordinates to the byte buffer
     *
     * @param buf The buffer to write to
     */
    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dim);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    /**
     * Reads the dimension and coordinates to the byte buffer
     *
     * @param buf The buffer to read from
     */
    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
    }

    /**
     * Handler class for the teleport request packet message
     */

    public static class Handler implements IMessageHandler<TeleportRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(TeleportRequestPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            ServerTickTaskQueue.schedule(() -> {
                if (player.isDead) return;
                if (!DebugActionAuthorization.isAuthorized(player)) return;

                if (!Double.isFinite(message.x) || !Double.isFinite(message.y) || !Double.isFinite(message.z)) {
                    return;
                }

                boolean allowed = DimensionTeleport.destinations()
                    .stream()
                    .anyMatch(destination -> destination.dimension() == message.dim);

                if (!allowed) return;

                DimensionTeleport.teleport(player, message.dim, message.x, message.y, message.z);
            });

            return null;
        }
    }

}
