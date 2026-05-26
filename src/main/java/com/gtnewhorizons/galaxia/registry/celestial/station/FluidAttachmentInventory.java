package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.List;

import net.minecraftforge.fluids.IFluidTank;

import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IFluidStorageHandler;

public class FluidAttachmentInventory implements IDistributedInventory {

    private final FluidAttachmentTank tank;

    public FluidAttachmentInventory(IFluidStorageHandler<?> handler, Object attachment) {
        this.tank = new FluidAttachmentTank(handler, attachment);
    }

    @Override
    public List<IFluidTank> getFluidTanks() {
        return List.of(tank);
    }
}
