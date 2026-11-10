package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Server-side bookkeeping of silos and rockets in flight. Uncrewed rockets travel here for their flight time, then
 * land in their destination silo even if nobody is around it
 */
public final class RocketFlights {

    /** How often flights are checked, in ticks */
    private static final int TICK_INTERVAL = 20;
    /** Delay before another landing attempt when the destination is busy */
    private static final int HOLD_RETRY_TICKS = 200;

    private static int tickCounter = 0;

    public static RocketWorldData data() {
        World overworld = DimensionManager.getWorld(0);
        if (overworld == null) return null;
        RocketWorldData data = (RocketWorldData) overworld.mapStorage
            .loadData(RocketWorldData.class, RocketWorldData.NAME);
        if (data == null) {
            data = new RocketWorldData(RocketWorldData.NAME);
            overworld.mapStorage.setData(RocketWorldData.NAME, data);
        }
        return data;
    }

    /** Current time used for flight scheduling */
    public static long now() {
        World overworld = DimensionManager.getWorld(0);
        return overworld == null ? 0 : overworld.getTotalWorldTime();
    }

    /**
     * Adds or renames a silo in the list of destinations
     *
     * @param dimension Dimension of the silo
     * @param pos       Controller position
     * @param name      Display name
     */
    public static void registerSilo(int dimension, BlockPos pos, String name) {
        RocketWorldData data = data();
        if (data == null) return;
        RocketWorldData.SiloRecord record = new RocketWorldData.SiloRecord(dimension, pos, name);
        if (record.equals(data.silos.get(record.key()))) return;
        data.silos.put(record.key(), record);
        data.markDirty();
    }

    public static void unregisterSilo(int dimension, BlockPos pos) {
        RocketWorldData data = data();
        if (data != null && data.silos.remove(RocketWorldData.keyOf(dimension, pos)) != null) data.markDirty();
    }

    public static List<RocketWorldData.SiloRecord> getSilos() {
        RocketWorldData data = data();
        return data == null ? List.of() : new ArrayList<>(data.silos.values());
    }

    /**
     * Checks whether a rocket is already heading to a silo, two rockets can't share a pad
     *
     * @param destination The silo to check
     *
     * @return Boolean : True => a rocket is on its way there
     */
    public static boolean isReserved(RocketDestination destination) {
        RocketWorldData data = data();
        if (data == null || !destination.isSilo()) return false;
        for (RocketFlight flight : data.flights) {
            if (flight.getDestination()
                .equals(destination)) return true;
        }
        return false;
    }

    /** Rockets that left a silo and have not landed yet */
    public static List<RocketFlight> getFlightsFrom(int dimension, BlockPos silo) {
        List<RocketFlight> result = new ArrayList<>();
        RocketWorldData data = data();
        if (data == null) return result;
        for (RocketFlight flight : data.flights) {
            if (flight.getSourceDimension() == dimension && silo.equals(flight.getSourceSilo())) result.add(flight);
        }
        return result;
    }

    public static void dispatch(RocketFlight flight) {
        RocketWorldData data = data();
        if (data == null) return;
        data.flights.add(flight);
        data.markDirty();
    }

    /**
     * Completes a flight right now, used when a crewed rocket touches down on its pad before the flight would have
     * landed on its own
     *
     * @param flightId The flight to complete
     *
     * @return The silo it landed in, or null if the flight is unknown or the pad is busy
     */
    public static TileEntitySilo landNow(UUID flightId) {
        RocketWorldData data = data();
        if (data == null) return null;
        for (Iterator<RocketFlight> iterator = data.flights.iterator(); iterator.hasNext();) {
            RocketFlight flight = iterator.next();
            if (!flight.getId()
                .equals(flightId)) continue;
            if (!tryLand(flight)) return null;
            iterator.remove();
            data.markDirty();
            return loadSilo(
                flight.getDestination()
                    .dimension(),
                flight.getDestination()
                    .silo());
        }
        return null;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        RocketWorldData data = data();
        if (data == null || data.flights.isEmpty()) return;

        long time = now();
        boolean changed = false;
        for (Iterator<RocketFlight> iterator = data.flights.iterator(); iterator.hasNext();) {
            RocketFlight flight = iterator.next();
            if (flight.getArrivalTime() > time) continue;
            if (tryLand(flight)) {
                iterator.remove();
            } else {
                flight.hold(time + HOLD_RETRY_TICKS);
            }
            changed = true;
        }
        if (changed) data.markDirty();
    }

    /**
     * Puts a rocket into its destination silo and tells the silo it came from
     *
     * @return Boolean : True => the rocket landed
     */
    private static boolean tryLand(RocketFlight flight) {
        TileEntitySilo target = loadSilo(
            flight.getDestination()
                .dimension(),
            flight.getDestination()
                .silo());
        if (target == null || !target.canReceiveRocket()) return false;

        // Credit the sender before the cargo is unloaded on the other side
        TileEntitySilo source = loadSilo(flight.getSourceDimension(), flight.getSourceSilo());
        if (source != null) source.onFlightLanded(flight);

        target.receiveRocket(flight);
        return true;
    }

    /**
     * Gets a silo, loading its dimension and chunk if needed
     *
     * @param dimension Dimension of the silo
     * @param pos       Controller position
     *
     * @return The silo, or null if it is gone
     */
    public static TileEntitySilo loadSilo(int dimension, BlockPos pos) {
        if (pos == null) return null;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;
        WorldServer world = server.worldServerForDimension(dimension);
        if (world == null) return null;
        TileEntity te = world.getTileEntity(pos.x(), pos.y(), pos.z());
        return te instanceof TileEntitySilo silo ? silo : null;
    }
}
