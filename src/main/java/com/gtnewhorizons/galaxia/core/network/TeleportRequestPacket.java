package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S1BPacketEntityAttach;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

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
    private int capsuleIndex;
    private String modules;
    private boolean hasRocket;

    public TeleportRequestPacket() {}

    public TeleportRequestPacket(int dim, double x, double y, double z) {
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
        this.hasRocket = false;
        this.modules = "";
    }

    public TeleportRequestPacket(int dim, double x, double y, double z, int capsuleIndex, List<Integer> modules) {
        this(dim, x, y, z);
        this.hasRocket = true;
        this.capsuleIndex = capsuleIndex;
        StringBuilder sb = new StringBuilder();
        for (int m : modules) {
            if (sb.length() > 0) sb.append(",");
            sb.append(m);
        }
        this.modules = sb.toString();
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
        buf.writeBoolean(hasRocket);
        if (hasRocket) {
            buf.writeInt(capsuleIndex);
            byte[] bytes = modules.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        }
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
        hasRocket = buf.readBoolean();
        if (hasRocket) {
            capsuleIndex = buf.readInt();
            int len = buf.readInt();
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            modules = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private List<Integer> parseModules() {
        List<Integer> list = new ArrayList<>();
        if (modules == null || modules.isEmpty()) return list;
        for (String part : modules.split(",")) {
            try {
                list.add(Integer.parseInt(part.trim()));
            } catch (Exception ignored) {}
        }
        return list;
    }

    /**
     * Handler class for the teleport request packet message
     */
    public static class Handler implements IMessageHandler<TeleportRequestPacket, IMessage> {

        private static final int SILO_SEARCH_RADIUS = 32;
        private static final int SILO_SEARCH_HEIGHT = 5;

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
                if (message.hasRocket) spawnLandingRocket(message, player, targetWorld);
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
                        double landY = message.hasRocket ? EntityRocket.SPAWN_ALTITUDE : message.y + 0.5;
                        double fallingMotionY = message.hasRocket ? EntityRocket.TERMINAL_FALL_SPEED : 0;
                        entity.setLocationAndAngles(
                            message.x,
                            landY,
                            message.z,
                            entity.rotationYaw,
                            entity.rotationPitch);
                        entity.fallDistance = 0.0F;
                        entity.motionX = entity.motionZ = 0.0D;
                        entity.motionY = fallingMotionY;
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

            if (message.hasRocket) spawnLandingRocket(message, player, targetWorld);
            return null;
        }

        public TileEntitySilo findNearbySilo(WorldServer world, double x, double z) {
            int groundY = world.getTopSolidOrLiquidBlock((int) x, (int) z);
            int searchX = (int) x;
            int searchZ = (int) z;
            for (int dx = -SILO_SEARCH_RADIUS; dx <= SILO_SEARCH_RADIUS; dx++) {
                for (int dz = -SILO_SEARCH_RADIUS; dz <= SILO_SEARCH_RADIUS; dz++) {
                    for (int dy = -SILO_SEARCH_HEIGHT; dy <= SILO_SEARCH_HEIGHT; dy++) {
                        TileEntity te = world.getTileEntity(searchX + dx, groundY + dy, searchZ + dz);
                        if (te instanceof TileEntitySilo silo && silo.isStructureValid()
                            && silo.getModules()
                                .isEmpty()) {
                            return silo;
                        }

                    }
                }
            }
            return null;
        }

        private void placePlayer(TeleportRequestPacket message, EntityPlayerMP player) {
            double landY = message.hasRocket ? EntityRocket.SPAWN_ALTITUDE : message.y + 0.5;
            double fallingMotionY = message.hasRocket ? EntityRocket.TERMINAL_FALL_SPEED : 0;
            player.setLocationAndAngles(message.x, landY, message.z, player.rotationYaw, player.rotationPitch);
            player.fallDistance = 0f;
            player.motionX = player.motionZ = 0;
            player.motionY = fallingMotionY;
        }

        private void spawnLandingRocket(TeleportRequestPacket message, EntityPlayerMP player, WorldServer world) {
            TileEntitySilo targetSilo = findNearbySilo(world, message.x, message.z);

            boolean inSilo = targetSilo != null;

            double landX = inSilo ? targetSilo.xCoord + TileEntitySilo.getRotatedOffset(
                TileEntitySilo.SILO_DEFAULT_X_OFFSET,
                TileEntitySilo.SILO_DEFAULT_Y_OFFSET,
                TileEntitySilo.SILO_DEFAULT_Z_OFFSET,
                targetSilo.currentFacing)[0] + 0.5 : message.x;

            double landZ = inSilo ? targetSilo.zCoord + TileEntitySilo.getRotatedOffset(
                TileEntitySilo.SILO_DEFAULT_X_OFFSET,
                TileEntitySilo.SILO_DEFAULT_Y_OFFSET,
                TileEntitySilo.SILO_DEFAULT_Z_OFFSET,
                targetSilo.currentFacing)[2] + 0.5 : message.z;

            EntityRocket lander = new EntityRocket(world);
            lander.setModules(message.parseModules());
            if (!inSilo) {
                lander.turnToLanderAndCache();
                lander.setCapsuleIndex(0);
            } else {
                lander.setCapsuleIndex(message.capsuleIndex);
            }
            lander.setPosition(landX, EntityRocket.SPAWN_ALTITUDE, landZ);
            lander.setTargetSilo(targetSilo);
            world.spawnEntityInWorld(lander);
            lander.beginLanding(landX, landZ);

            int[] ticksWaited = { 0 };
            ServerTickTaskQueue.scheduleWhen(() -> {
                ticksWaited[0]++;
                return player.dimension == message.dim && !player.isDead && !lander.isDead && ticksWaited[0] >= 5;
            }, () -> {
                player.mountEntity(lander);
                player.playerNetServerHandler.sendPacket(new S1BPacketEntityAttach(0, player, lander));
            });
        }
    }
}
