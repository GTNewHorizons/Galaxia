package com.gtnewhorizons.galaxia.registry.block.tile;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;

public abstract class TileStationSecondary<T extends TileStationBase<T>> extends TileStationBase<T> {

    public TileStationSecondary() {
        super();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
    }

    @Override
    protected boolean attemptBoot() {
        for (BlockPos pos : airlocks) {
            if (!(pos.getTE(worldObj) instanceof TileEntityAirlock airlock)) continue;
            for (BlockPos other : airlock.getStationControllers()) {
                if (other.equals(here)) continue;
                if (!(other.getTE(worldObj) instanceof TileStationBase<?>base)) continue;
                if (base.graph != null) {
                    this.graph = base.graph;
                    graph.connectPiece(here);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onStructureDisformed() {
        if (graph != null) {
            graph.disconnectPiece(here);
        }
        super.onStructureDisformed();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (graph != null) {
            graph.disconnectPiece(here);
        }
    }

    @Override
    public void onPieceConnected(TileStationBase<?> piece, TileStationBase<?> neighbor, BlockPos controllerPos) {
        if ((piece == this || neighbor == this) && controllerPos != null) {
            if (controllerPos.getTE(worldObj) instanceof TileStationController controller) {
                graph = controller.getGraph();
            }
        }
    }

    @Override
    public void onPieceDisconnected(TileStationBase<?> piece, TileStationBase<?> neighbor) {
        graph = null;
    }

    @Override
    public void onGraphRebuilt(TileStationController controller) {
        if (!structureValid || controller.getGraph() == null) return;
        graph = controller.getGraph();
    }

    public int getVolume() {
        if (getStructureDefinition() instanceof ArbitraryShapeDefinition<?>def) {
            return def.getVolume();
        }

        return 0;
    }
}
