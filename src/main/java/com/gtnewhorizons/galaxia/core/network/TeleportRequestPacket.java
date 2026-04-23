package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S1BPacketEntityAttach;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocketSeat;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class TeleportRequestPacket implements IMessage {

    private int dim;
    private double x, y, z;
    private int capsuleIndex;
    private String modules;
    private boolean hasRocket;
    private List<UUID> passengerUUIDs = new ArrayList<>();

    public TeleportRequestPacket() {}

    public TeleportRequestPacket(int dim, double x, double y, double z) {
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
        this.hasRocket = false;
        this.modules = "";
    }

    public TeleportRequestPacket(int dim, double x, double y, double z, int capsuleIndex, List<Integer> modules,
        List<UUID> passengerUUIDs) {
        this(dim, x, y, z);
        this.hasRocket = true;
        this.capsuleIndex = capsuleIndex;
        this.passengerUUIDs = passengerUUIDs;
        StringBuilder sb = new StringBuilder();
        for (int m : modules) {
            if (sb.length() > 0) sb.append(",");
            sb.append(m);
        }
        this.modules = sb.toString();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dim);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeBoolean(hasRocket);
        if (hasRocket) {
            buf.writeInt(capsuleIndex);

            // Write UUIDs
            buf.writeInt(passengerUUIDs.size());
            for (UUID uuid : passengerUUIDs) {
                buf.writeLong(uuid.getMostSignificantBits());
                buf.writeLong(uuid.getLeastSignificantBits());
            }

            byte[] bytes = modules.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        hasRocket = buf.readBoolean();
        if (hasRocket) {
            capsuleIndex = buf.readInt();

            // Read UUIDs
            int passengerCount = buf.readInt();
            passengerUUIDs = new ArrayList<>(passengerCount);
            for (int i = 0; i < passengerCount; i++) {
                passengerUUIDs.add(new UUID(buf.readLong(), buf.readLong()));
            }

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

    public static class Handler implements IMessageHandler<TeleportRequestPacket, IMessage> {

        private static final int SILO_SEARCH_RADIUS = 32;
        private static final int SILO_SEARCH_HEIGHT = 5;

        @Override
        public IMessage onMessage(TeleportRequestPacket message, MessageContext ctx) {
            MinecraftServer server = MinecraftServer.getServer();
            WorldServer targetWorld = server.worldServerForDimension(message.dim);

            if (targetWorld == null) return null;

            // 1. Gather all players to be teleported
            List<EntityPlayerMP> players = new ArrayList<>();
            for (UUID uuid : message.passengerUUIDs) {
                for (Object obj : server.getConfigurationManager().playerEntityList) {
                    if (obj instanceof EntityPlayerMP player && player.getUniqueID()
                        .equals(uuid)) {
                        players.add(player);
                        break;
                    }
                }
            }

            if (players.isEmpty() && !message.hasRocket) return null;

            // 2. Spawn the rocket ONCE in the target dimension
            EntityRocket lander = null;
            if (message.hasRocket) {
                lander = spawnLandingRocket(message, targetWorld);
            }

            // 3. Teleport and Remount everyone
            for (int i = 0; i < players.size(); i++) {
                EntityPlayerMP p = players.get(i);
                p.mountEntity(null);

                if (p.dimension != message.dim) {
                    server.getConfigurationManager()
                        .transferPlayerToDimension(p, message.dim, new Teleporter(targetWorld) {

                            @Override
                            public void placeInPortal(Entity entity, double px, double py, double pz, float yaw) {
                                placePlayer(message, entity);
                            }

                            @Override
                            public boolean makePortal(Entity entity) {
                                return true;
                            }
                        });
                } else {
                    placePlayer(message, p);
                }

                if (lander != null) {
                    scheduleMount(p, lander, i, message.dim);
                }
            }
            return null;
        }

        private void placePlayer(TeleportRequestPacket message, Entity entity) {
            double landY = message.hasRocket ? EntityRocket.SPAWN_ALTITUDE : message.y + 0.5;
            double fallingMotionY = message.hasRocket ? EntityRocket.TERMINAL_FALL_SPEED : 0;
            entity.setLocationAndAngles(message.x, landY, message.z, entity.rotationYaw, entity.rotationPitch);
            entity.fallDistance = 0.0F;
            entity.motionX = entity.motionZ = 0.0D;
            entity.motionY = fallingMotionY;
        }

        private EntityRocket spawnLandingRocket(TeleportRequestPacket message, WorldServer world) {
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
                // Now strips everything except Lander & Rider Modules
                lander.turnToLanderAndCache();
                lander.setCapsuleIndex(0);
            } else {
                lander.setCapsuleIndex(message.capsuleIndex);
            }

            lander.setPosition(landX, EntityRocket.SPAWN_ALTITUDE, landZ);
            lander.setTargetSilo(targetSilo);
            world.spawnEntityInWorld(lander);

            lander.initializeSeats();
            lander.beginLanding(landX, landZ);

            return lander;
        }

        private void scheduleMount(EntityPlayerMP player, EntityRocket lander, int riderIndex, int targetDim) {
            int[] ticksWaited = { 0 };
            ServerTickTaskQueue.scheduleWhen(() -> {
                ticksWaited[0]++;
                return player.dimension == targetDim && !player.isDead && !lander.isDead && ticksWaited[0] >= 5;
            }, () -> {
                Entity targetSeat = lander; // Pilot gets main entity

                // Passengers get seats
                if (riderIndex > 0) {
                    int seatIndex = riderIndex - 1;
                    List<EntityRocketSeat> seats = lander.getPassengerSeats();
                    if (seatIndex < seats.size()) {
                        targetSeat = seats.get(seatIndex);
                    }
                }

                player.mountEntity(targetSeat);
                player.playerNetServerHandler.sendPacket(new S1BPacketEntityAttach(0, player, targetSeat));
            });
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

    }
}
