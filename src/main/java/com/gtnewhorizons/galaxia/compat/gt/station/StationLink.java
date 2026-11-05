package com.gtnewhorizons.galaxia.compat.gt.station;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationLookup;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The station a GT machine stands in
 */
public final class StationLink {

    private final StationLookup lookup = new StationLookup();

    /** Graph of the station around the machine, null outside a station */
    @Nullable
    public StationGraph graph(IGregTechTileEntity base) {
        return lookup.graph(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
    }

    /** Shared storage of the station around the machine, null outside a station */
    @Nullable
    public IDistributedInventory storage(IGregTechTileEntity base) {
        return lookup.storage(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
    }
}
