package com.gtnewhorizons.galaxia.registry.rocketmodules.utility;

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

import com.gtnewhorizons.galaxia.core.network.ServerTickTaskQueue;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocketSeat;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

public class RocketTeleportHelper {

    private static final int SILO_SEARCH_RADIUS = 32;
    private static final int SILO_SEARCH_HEIGHT = 5;

    /**
     * Executes the teleportation of players and handles spawning the landing rocket
     * in the new dimension.
     * Must be called on the server side.
     */
    public static void teleportPlayers(int targetDim, double x, double y, double z, boolean hasRocket, int capsuleIndex,
        List<Integer> modules, List<UUID> passengerUUIDs) {

        MinecraftServer server = MinecraftServer.getServer();
        WorldServer targetWorld = server.worldServerForDimension(targetDim);

        if (targetWorld == null) return;

        List<EntityPlayerMP> players = new ArrayList<>();
        for (UUID uuid : passengerUUIDs) {
            for (Object obj : server.getConfigurationManager().playerEntityList) {
                if (obj instanceof EntityPlayerMP player && player.getUniqueID()
                    .equals(uuid)) {
                    players.add(player);
                    break;
                }
            }
        }

        if (players.isEmpty() && !hasRocket) return;

        // 2. Spawn the rocket ONCE in the target dimension
        EntityRocket lander = null;
        if (hasRocket) {
            lander = spawnLandingRocket(targetWorld, x, z, capsuleIndex, modules);
        }

        // 3. Teleport and Remount everyone
        for (int i = 0; i < players.size(); i++) {
            EntityPlayerMP p = players.get(i);
            p.mountEntity(null); // Ensure dismounted before dimension transfer

            if (p.dimension != targetDim) {
                server.getConfigurationManager()
                    .transferPlayerToDimension(p, targetDim, new Teleporter(targetWorld) {

                        @Override
                        public void placeInPortal(Entity entity, double px, double py, double pz, float yaw) {
                            placePlayer(entity, x, y, z, hasRocket);
                        }

                        @Override
                        public boolean makePortal(Entity entity) {
                            return true;
                        }
                    });
            } else {
                placePlayer(p, x, y, z, hasRocket);
            }

            // Schedule them to be put back into the seats
            if (lander != null) {
                scheduleMount(p, lander, i, targetDim);
            }
        }
    }

    private static void placePlayer(Entity entity, double targetX, double targetY, double targetZ, boolean hasRocket) {
        double landY = hasRocket ? EntityRocket.SPAWN_ALTITUDE : targetY + 0.5;
        double fallingMotionY = hasRocket ? EntityRocket.TERMINAL_FALL_SPEED : 0;
        entity.setLocationAndAngles(targetX, landY, targetZ, entity.rotationYaw, entity.rotationPitch);
        entity.fallDistance = 0.0F;
        entity.motionX = entity.motionZ = 0.0D;
        entity.motionY = fallingMotionY;
    }

    private static EntityRocket spawnLandingRocket(WorldServer world, double x, double z, int capsuleIndex,
        List<Integer> modules) {
        TileEntitySilo targetSilo = findNearbySilo(world, x, z);
        boolean inSilo = targetSilo != null;

        // Calculate landing coordinates (center of silo if found, otherwise raw coords)
        double landX = inSilo ? targetSilo.xCoord + TileEntitySilo.getRotatedOffset(
            TileEntitySilo.SILO_DEFAULT_X_OFFSET,
            TileEntitySilo.SILO_DEFAULT_Y_OFFSET,
            TileEntitySilo.SILO_DEFAULT_Z_OFFSET,
            targetSilo.currentFacing)[0] + 0.5 : x;

        double landZ = inSilo ? targetSilo.zCoord + TileEntitySilo.getRotatedOffset(
            TileEntitySilo.SILO_DEFAULT_X_OFFSET,
            TileEntitySilo.SILO_DEFAULT_Y_OFFSET,
            TileEntitySilo.SILO_DEFAULT_Z_OFFSET,
            targetSilo.currentFacing)[2] + 0.5 : z;

        EntityRocket lander = new EntityRocket(world);
        lander.setModules(modules);

        if (!inSilo) {
            // Strip everything except Lander & Rider Modules
            lander.turnToLanderAndCache();
            lander.setCapsuleIndex(0);
        } else {
            lander.setCapsuleIndex(capsuleIndex);
        }

        lander.setPosition(landX, EntityRocket.SPAWN_ALTITUDE, landZ);
        lander.setTargetSilo(targetSilo);
        world.spawnEntityInWorld(lander);

        lander.initializeSeats();
        lander.beginLanding(landX, landZ);

        return lander;
    }

    private static void scheduleMount(EntityPlayerMP player, EntityRocket lander, int riderIndex, int targetDim) {
        int[] ticksWaited = { 0 };
        ServerTickTaskQueue.scheduleWhen(() -> {
            ticksWaited[0]++;
            // Wait for player dimension sync and ensure they are ready to mount
            return player.dimension == targetDim && !player.isDead && !lander.isDead && ticksWaited[0] >= 5;
        }, () -> {
            Entity targetSeat = lander; // Pilot (index 0) gets main entity

            // Passengers get extra seats
            if (riderIndex > 0) {
                int seatIndex = riderIndex - 1;
                List<EntityRocketSeat> seats = lander.getPassengerSeats();
                if (seatIndex < seats.size()) {
                    targetSeat = seats.get(seatIndex);
                }
            }

            player.mountEntity(targetSeat);
            // Force client update immediately
            player.playerNetServerHandler.sendPacket(new S1BPacketEntityAttach(0, player, targetSeat));
        });
    }

    private static TileEntitySilo findNearbySilo(WorldServer world, double x, double z) {
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
