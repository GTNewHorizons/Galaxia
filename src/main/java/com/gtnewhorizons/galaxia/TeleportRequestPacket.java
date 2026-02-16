package com.gtnewhorizons.galaxia;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

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

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dim);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
    }

    public static class Handler implements IMessageHandler<TeleportRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(TeleportRequestPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            MinecraftServer server = player.mcServer;
            WorldServer targetWorld = server.worldServerForDimension(message.dim);

            if (targetWorld == null) return null;

            // Если в том же измерении — просто перемещаем
            if (player.dimension == message.dim) {
                player.setLocationAndAngles(
                    message.x,
                    message.y + 0.5,
                    message.z,
                    player.rotationYaw,
                    player.rotationPitch);
                player.fallDistance = 0.0F;
                player.motionX = player.motionY = player.motionZ = 0.0D;
                return null;
            }

            // Иначе — трансфер с кастомным Teleporter
            server.getConfigurationManager()
                .transferPlayerToDimension(player, message.dim, new Teleporter(targetWorld) {

                    @Override
                    public void placeInPortal(Entity entity, double px, double py, double pz, float yaw) {
                        entity.setLocationAndAngles(
                            message.x,
                            message.y + 0.5,
                            message.z,
                            entity.rotationYaw,
                            entity.rotationPitch);
                        entity.fallDistance = 0.0F;
                        entity.motionX = entity.motionY = entity.motionZ = 0.0D;
                    }

                    @Override
                    public boolean makePortal(Entity entity) {
                        return true;
                    }
                });

            return null;
        }
    }
}
