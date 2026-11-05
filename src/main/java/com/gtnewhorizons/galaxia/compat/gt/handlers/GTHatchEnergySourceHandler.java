package com.gtnewhorizons.galaxia.compat.gt.handlers;

import java.math.BigInteger;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnergyHandler;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;

/**
 * Lets a hatch hand its energy to the station: an energy hatch in a room's wall fed by cables outside, or a dynamo
 * hatch of a generator inside a room. The station draws from its buffer, it never charges it
 */
public class GTHatchEnergySourceHandler implements IEnergyHandler<MTEHatch> {

    @Override
    public BlockPos getPosition(MTEHatch hatch) {
        IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
        return new BlockPos(base.getXCoord(), base.getYCoord(), base.getZCoord());
    }

    @Override
    public void tick(MTEHatch hatch) {}

    @Override
    public boolean isReady(MTEHatch hatch) {
        return hatch.getBaseMetaTileEntity() != null;
    }

    @Override
    public void onAttached(MTEHatch hatch, StationGraph graph) {}

    @Override
    public void onDetached(MTEHatch hatch, StationGraph graph) {}

    @Override
    public void markDirty(MTEHatch hatch) {}

    @Override
    public BigInteger getEnergyStored(MTEHatch hatch) {
        return BigInteger.valueOf(
            hatch.getBaseMetaTileEntity()
                .getStoredEU());
    }

    @Override
    public BigInteger getEnergyCapacity(MTEHatch hatch) {
        return BigInteger.valueOf(
            hatch.getBaseMetaTileEntity()
                .getEUCapacity());
    }

    @Override
    public long getPassiveDrain(MTEHatch hatch) {
        return 0;
    }

    @Override
    public long getInputRate(MTEHatch hatch) {
        return 0;
    }

    @Override
    public long getOutputRate(MTEHatch hatch) {
        return 0;
    }

    @Override
    public long drawEnergy(MTEHatch hatch, long amount) {
        IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
        long drawn = Math.min(amount, base.getStoredEU());
        if (drawn > 0) base.decreaseStoredEnergyUnits(drawn, true);
        return Math.max(drawn, 0);
    }
}
