package com.gtnewhorizons.galaxia.compat.gt.station;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.gt.MTEStationPlug;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;

/**
 * Energy and dynamo hatches inside a station trade energy with its stores, so machines need no cables there: energy
 * hatches are fed by the station, dynamo hatches charge it
 */
public final class StationEnergyExchange {

    private StationEnergyExchange() {}

    /** Tops an energy hatch up from the station once half its buffer is used, at most one tick of its input */
    public static void feed(MTEHatchEnergy hatch, StationLink link) {
        IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;
        long stored = base.getStoredEU();
        if (stored >= hatch.maxEUStore() / 2) return;
        StationGraph graph = link.graph(base);
        // A hatch the station draws from is its way in, not a machine to feed
        if (graph == null || graph.hasAttachment(new BlockPos(base.getXCoord(), base.getYCoord(), base.getZCoord())))
            return;
        long wanted = Math.min(hatch.maxEUStore() - stored, hatch.maxEUInput() * hatch.maxAmperesIn());
        long drawn = graph.drawEnergy(wanted);
        if (drawn > 0) base.increaseStoredEnergyUnits(drawn, true);
    }

    /** Hands what a dynamo hatch holds to the station, at most one tick of its output */
    public static void collect(MTEHatchDynamo hatch, StationLink link) {
        // Station plugs carry the station's own energy out to cables
        if (hatch instanceof MTEStationPlug) return;
        IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;
        long stored = base.getStoredEU();
        if (stored <= 0) return;
        StationGraph graph = link.graph(base);
        if (graph == null) return;
        long added = graph.addEnergy(Math.min(stored, hatch.maxEUOutput() * hatch.maxAmperesOut()));
        if (added > 0) base.decreaseStoredEnergyUnits(added, true);
    }
}
