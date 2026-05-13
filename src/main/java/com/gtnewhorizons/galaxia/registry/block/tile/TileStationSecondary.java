package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.List;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.registry.interfaces.IGraphListener;

public abstract class TileStationSecondary<T extends TileStationBase<T>> extends TileStationBase<T>
    implements IGraphListener {

    public TileStationSecondary() {
        super();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        StationGraph graph = findControllerGraph();
        if (graph != null) {
            graph.addListener(this);
            graph.rebuild();
        }
    }

    @Override
    public void onStructureDisformed() {
        StationGraph graph = findControllerGraph();
        if (graph != null) {
            graph.removeListener(this);
        }
        List<BlockPos> savedAirlocks = new java.util.ArrayList<>(this.airlocks);
        super.onStructureDisformed();
        triggerControllerGraphRebuild(savedAirlocks);
    }

    @Override
    public void invalidate() {
        List<BlockPos> savedAirlocks = new java.util.ArrayList<>(this.airlocks);
        super.invalidate();
        triggerControllerGraphRebuild(savedAirlocks);
    }

    private StationGraph findControllerGraph() {
        for (BlockPos airlockPos : airlocks) {
            TileEntityAirlock airlock = airlockPos.getTE(worldObj);
            if (airlock == null) continue;
            for (BlockPos other : airlock.getStationControllers()) {
                if (other.equals(this.here)) continue;
                TileStationBase<?> piece = other.getTE(worldObj);
                if (piece instanceof TileStationController ctrl) {
                    return ctrl.getGraph();
                }
            }
        }
        return null;
    }

    /**
     * Finds a controller through saved airlock positions and triggers rebuild.
     * Called after this piece has been untracked from airlocks, so existing
     * airlocks list may be empty.
     */
    protected void triggerControllerGraphRebuild(List<BlockPos> airlockPositions) {
        for (BlockPos airlockPos : airlockPositions) {
            TileEntityAirlock airlock = airlockPos.getTE(worldObj);
            if (airlock == null) continue;
            for (BlockPos other : airlock.getStationControllers()) {
                if (other.equals(this.here)) continue;
                TileStationBase<?> piece = other.getTE(worldObj);
                if (piece instanceof TileStationController ctrl) {
                    StationGraph graph = ctrl.getGraph();
                    if (graph != null) {
                        graph.rebuild();
                    }
                    return;
                }
            }
        }
    }

    public int getVolume() {
        if (getStructureDefinition() instanceof ArbitraryShapeDefinition<?>def) {
            return def.getVolume();
        }

        return 0;
    }
}
