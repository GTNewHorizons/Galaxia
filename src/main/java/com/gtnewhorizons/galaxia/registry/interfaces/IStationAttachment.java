package com.gtnewhorizons.galaxia.registry.interfaces;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBootableMultiblock;
import com.gtnewhorizons.galaxia.registry.block.tile.StationGraph;

public interface IStationAttachment<T extends GalaxiaBootableMultiblock<T>> {

    default void onAttached(StationGraph graph) {}

    default void onDetached(StationGraph graph) {}

    BlockPos getPosition();

    void tick();

    default boolean isReady() {
        return ((T) this).booted();
    }
}
