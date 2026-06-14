package com.gtnewhorizons.galaxia.registry.interfaces;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStationBase;

public interface IGraphListener {

    default void onPieceConnected(TileStationBase<?> piece, TileStationBase<?> neighbor, BlockPos controllerPos) {}

    default void onPieceDisconnected(TileStationBase<?> piece, TileStationBase<?> neighbor) {}

    default void onGraphRebuilt(TileStation controller) {}

    default void onAttachmentConnected(BlockPos pos, Object attachment) {}

    default void onAttachmentDisconnected(BlockPos pos) {}
}
