package com.gtnewhorizons.galaxia.registry.interfaces;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.tile.StationGraph;

public interface StationAttachment {

    default void onAttached(StationGraph graph) {}

    default void onDetached(StationGraph graph) {}

    BlockPos getPosition();
}
