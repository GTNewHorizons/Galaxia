package com.gtnewhorizons.galaxia.registry.rocketmodules.utility;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S1BPacketEntityAttach;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.galaxia.core.network.ServerTickTaskQueue;
import com.gtnewhorizons.galaxia.registry.dimension.OrbitDimensions;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocketSeat;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketDestination;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketFlight;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketFlights;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

/**
 * Moves the crew of a departing rocket to its destination and puts them into a rocket descending onto the pad
 */
public class RocketTeleportHelper {

    /** Height a capsule stops at when it arrives in an orbit without a silo */
    private static final int ORBIT_ARRIVAL_Y = 100;

    /** A crewed flight lands on its own after this many ticks if the descending rocket never touches down */
    private static final int CREWED_LANDING_TIMEOUT = 2400;

    /**
     * Ends the climb of a crewed rocket: spawns the descending rocket at the destination and moves the crew into it.
     * Must be called on the server side
     *
     * @param rocket The rocket leaving the launch site
     * @param flight What survived the burn and where it goes
     */
    public static void departCrewed(EntityRocket rocket, RocketFlight flight) {
        MinecraftServer server = MinecraftServer.getServer();
        RocketDestination destination = flight.getDestination();
        WorldServer targetWorld = server.worldServerForDimension(destination.dimension());
        if (targetWorld == null) return;

        List<EntityPlayer> crew = rocket.getCrew();
        TileEntitySilo silo = RocketFlights.loadSilo(destination.dimension(), destination.silo());

        EntityRocket lander = new EntityRocket(targetWorld);
        lander.setBlueprint(flight.getBlueprint());
        double x;
        double z;
        double padY;
        if (silo != null) {
            int[] offset = silo.getRocketOffset();
            x = silo.xCoord + offset[0] + 0.5;
            z = silo.zCoord + offset[2] + 0.5;
            padY = silo.yCoord + offset[1];

            // Keeps the pad reserved while descending, and lands the rocket anyway if the lander gets lost
            RocketFlights.dispatch(
                new RocketFlight(
                    flight.getId(),
                    flight.getBlueprint(),
                    flight.getLoadout(),
                    flight.getSourceDimension(),
                    flight.getSourceSilo(),
                    destination,
                    RocketFlights.now() + CREWED_LANDING_TIMEOUT));
            lander.setTargetSilo(silo);
            lander.setPosition(x, padY + EntityRocket.LANDING_START_HEIGHT, z);
            lander.beginLanding(flight.getId(), padY);
        } else {
            // No silo at the other end, the capsule touches down on the surface below the crew. Orbits have no
            // surface, there it stops at a fixed height
            x = rocket.posX;
            z = rocket.posZ;
            padY = OrbitDimensions.isOrbit(destination.dimension()) ? ORBIT_ARRIVAL_Y
                : targetWorld.getTopSolidOrLiquidBlock((int) Math.floor(x), (int) Math.floor(z));
            lander.turnToLanderAndCache();
            lander.setPosition(x, padY + EntityRocket.LANDING_START_HEIGHT, z);
            lander.beginLanding(null, padY);
        }
        targetWorld.spawnEntityInWorld(lander);
        lander.initializeSeats();

        for (int i = 0; i < crew.size(); i++) {
            if (!(crew.get(i) instanceof EntityPlayerMP player)) continue;
            player.mountEntity(null);
            movePlayer(player, targetWorld, x, padY + EntityRocket.LANDING_START_HEIGHT, z);
            scheduleMount(player, lander, i, destination.dimension());
        }
    }

    private static void movePlayer(EntityPlayerMP player, WorldServer targetWorld, double x, double y, double z) {
        if (player.dimension != targetWorld.provider.dimensionId) {
            MinecraftServer.getServer()
                .getConfigurationManager()
                .transferPlayerToDimension(player, targetWorld.provider.dimensionId, new Teleporter(targetWorld) {

                    @Override
                    public void placeInPortal(Entity entity, double px, double py, double pz, float yaw) {
                        placePlayer(entity, x, y, z);
                    }

                    @Override
                    public boolean makePortal(Entity entity) {
                        return true;
                    }
                });
        } else {
            placePlayer(player, x, y, z);
            player.playerNetServerHandler.setPlayerLocation(x, y, z, player.rotationYaw, player.rotationPitch);
        }
    }

    private static void placePlayer(Entity entity, double x, double y, double z) {
        entity.setLocationAndAngles(x, y, z, entity.rotationYaw, entity.rotationPitch);
        entity.fallDistance = 0.0F;
        entity.motionX = entity.motionZ = 0.0D;
        entity.motionY = EntityRocket.TERMINAL_FALL_SPEED;
    }

    private static void scheduleMount(EntityPlayerMP player, EntityRocket lander, int riderIndex, int targetDim) {
        int[] ticksWaited = { 0 };
        ServerTickTaskQueue.scheduleWhen(() -> {
            ticksWaited[0]++;
            return player.dimension == targetDim && !player.isDead && !lander.isDead && ticksWaited[0] >= 5;
        }, () -> {
            Entity targetSeat = lander;
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
}
