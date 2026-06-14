package com.gtnewhorizons.galaxia.registry.interfaces;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.gt.MTEHatchStationMaintenance;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

public interface IAttachmentHandler<T> {

    BlockPos getPosition(T attachment);

    void tick(T attachment);

    boolean isReady(T attachment);

    void onAttached(T attachment, StationGraph graph);

    void onDetached(T attachment, StationGraph graph);

    default boolean hasDistributedInventory() {
        return false;
    }

    void markDirty(T attachment);

    default <G extends MTEMultiBlockBase> MTEHatchStationMaintenance getStationMaintanece(G attachment) {
        if (attachment.mMaintenanceHatches.isEmpty()) return null;
        if (attachment.mMaintenanceHatches.getFirst() instanceof MTEHatchStationMaintenance m) return m;
        return null;
    }
}
