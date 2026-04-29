package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

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
            MinecraftServer server = player.mcServer;
            WorldServer targetWorld = server.worldServerForDimension(message.dim);

            if (targetWorld == null) return null;

            if (player.dimension == message.dim) {
                placePlayer(message, player);
                return null;
            }

            player.mountEntity(null);
            server.getConfigurationManager()
                .transferPlayerToDimension(player, message.dim, new Teleporter(targetWorld) {

                    /**
                     * Overriding the method to place entity in a new location (portal)
                     *
                     * @param entity The entity to move
                     * @param px     Portal x coordinate
                     * @param py     Portal y coordinate
                     * @param pz     Portal z coordinate
                     * @param yaw    The desired yaw of the entity
                     */
                    @Override
                    public void placeInPortal(Entity entity, double px, double py, double pz, float yaw) {
                        entity.setLocationAndAngles(
                            message.x,
                            message.y,
                            message.z,
                            entity.rotationYaw,
                            entity.rotationPitch);
                    }

                    /**
                     * Can ignore - just required for override
                     *
                     * @param entity Entity to transport
                     * @return true
                     */
                    @Override
                    public boolean makePortal(Entity entity) {
                        return true;
                    }
                });

            return null;
        }

        private void placePlayer(TeleportRequestPacket message, EntityPlayerMP player) {
            double landY = message.y + 0.5;
            player.setLocationAndAngles(message.x, landY, message.z, player.rotationYaw, player.rotationPitch);
            player.fallDistance = 0f;
            player.motionX = player.motionY = player.motionZ = 0;
        }

    }
}
