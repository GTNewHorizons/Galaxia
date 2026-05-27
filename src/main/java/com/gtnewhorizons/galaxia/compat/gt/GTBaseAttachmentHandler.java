package com.gtnewhorizons.galaxia.compat.gt;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.interfaces.IAttachmentHandler;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

public abstract class GTBaseAttachmentHandler<T extends MTEMultiBlockBase> implements IAttachmentHandler<T> {

    public BlockPos getPosition(T attachment) {
        IGregTechTileEntity base = attachment.getBaseMetaTileEntity();
        if (base == null) return null;
        return new BlockPos(base.getXCoord(), base.getYCoord(), base.getZCoord());
    }

    public void tick(T attachment) {}

    public boolean isReady(T attachment) {
        IGregTechTileEntity base = attachment.getBaseMetaTileEntity();
        return base != null && !base.isDead() && getHatch(attachment) != null;
    }

    public void onAttached(T attachment, StationGraph graph) {
        var mHatch = getHatch(attachment);
        if (mHatch == null) return;
        mHatch.setGraph(graph);
    }

    public void onDetached(T attachment, StationGraph graph) {
        var mHatch = getHatch(attachment);
        if (mHatch == null) return;
        mHatch.setGraph(null);
    }

    public void markDirty(T attachment) {
        attachment.markDirty();
    }

    protected MTEHatchStationMaintenance getHatch(T attachment) {
        if (attachment.mMaintenanceHatches.isEmpty()) return null;
        if (attachment.mMaintenanceHatches.getFirst() instanceof MTEHatchStationMaintenance m) return m;
        return null;
    }
}
