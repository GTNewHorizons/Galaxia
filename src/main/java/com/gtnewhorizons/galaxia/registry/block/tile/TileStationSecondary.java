package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.galaxia.api.BlockPos;

public abstract class TileStationSecondary<T extends TileStationBase<T>> extends TileStationBase<T> {

    protected @Nullable BlockPos mainController;

    public TileStationSecondary() {
        super();
    }

    public void collectGraph(TileStationController controller, List<BlockPos> monitors) {
        mainController = controller.here;

        for (BlockPos pos : airlocks) {
            TileEntityAirlock airlock = pos.getTE(worldObj);
            if (airlock == null) continue;

            airlock.collectGraph(controller, monitors);
        }
    }

    public boolean tryRebuildControllersGraph() {
        if (mainController != null) {
            TileStationController controller = mainController.getTE(worldObj);
            if (controller == null) return false;

            return controller.tryRebuildControllersGraph();
        }

        return false;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (mainController != null) {
            nbt.setTag("mainController", mainController.toNBT());
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("mainController")) {
            mainController = BlockPos.fromNBT(nbt.getCompoundTag("mainController"));
        }
    }
}
