package com.gtnewhorizons.galaxia.registry.celestial.station;

import javax.annotation.Nullable;

import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.registry.dimension.OrbitDimensions;

/**
 * The station room a block stands in or is built into. The room is looked up again every few seconds, so machines
 * follow rooms that
 * are built, joined or torn down around them
 */
public final class StationLookup {

    /** Ticks between two lookups of the room around the block */
    private static final int LOOKUP_INTERVAL = 100;

    private @Nullable TileStation room;
    private long nextLookup;

    /** Room around the position, null outside a station */
    @Nullable
    public TileStation room(World world, int x, int y, int z) {
        // Stations only stand in orbit, so blocks elsewhere never look
        if (world == null || !OrbitDimensions.isOrbit(world.provider.dimensionId)) return null;
        long now = world.getTotalWorldTime();
        boolean lost = room != null && (room.isInvalid() || !room.isStructureValid());
        if (now >= nextLookup || lost) {
            nextLookup = now + LOOKUP_INTERVAL;
            room = GalaxiaAPI.getStationContaining(world, world.provider.dimensionId, x, y, z);
        }
        return room;
    }

    /** Graph of the station around the position, null outside a station */
    @Nullable
    public StationGraph graph(World world, int x, int y, int z) {
        TileStation found = room(world, x, y, z);
        return found == null ? null : found.getGraph();
    }

    /** Shared storage of the station around the position, null outside a station */
    @Nullable
    public Station storage(World world, int x, int y, int z) {
        StationGraph graph = graph(world, x, y, z);
        if (graph == null || graph.getController() == null) return null;
        return graph.getController()
            .getBackingStation();
    }
}
