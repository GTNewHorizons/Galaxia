package com.gtnewhorizons.galaxia.registry.interfaces;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;

public interface IAttachmentHandler<T> {

    BlockPos getPosition(T attachment);

    void tick(T attachment);

    boolean isReady(T attachment);

    default void onAttached(T attachment, StationGraph graph) {}

    default void onDetached(T attachment, StationGraph graph) {}

    default boolean hasDistributedInventory() {
        return false;
    }

    void markDirty(T attachment);
}
