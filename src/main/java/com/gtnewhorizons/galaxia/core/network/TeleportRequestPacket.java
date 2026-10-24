package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.DimensionManager;

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

        /**
         * Handler for on sending a new packet request
         *
         * @param message The message being sent
         * @param ctx     The message context
         * @return null - signature can be ignored, only there due to override
         */
        @Override
        public IMessage onMessage(TeleportRequestPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ServerTickTaskQueue.schedule(() -> {
                if (com.gtnewhorizons.galaxia.core.Galaxia.proxy.serverRuntime()
                    .restrictedPassenger(player.getUniqueID()) != null) return;
                if (!DimensionManager.isDimensionRegistered(message.dim)) return;
                DimensionTeleport.teleport(player, message.dim, message.x, message.y, message.z);
            });
            return null;
        }
    }
}
