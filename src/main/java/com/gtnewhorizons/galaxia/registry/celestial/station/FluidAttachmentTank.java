package com.gtnewhorizons.galaxia.registry.celestial.station;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

import com.gtnewhorizons.galaxia.registry.interfaces.IFluidStorageHandler;

public class FluidAttachmentTank implements IFluidTank {

    private final IFluidStorageHandler<Object> handler;
    private final Object attachment;

    @SuppressWarnings("unchecked")
    public FluidAttachmentTank(IFluidStorageHandler<?> handler, Object attachment) {
        this.handler = (IFluidStorageHandler<Object>) handler;
        this.attachment = attachment;
    }

    @Override
    public FluidStack getFluid() {
        return handler.drainFluid(attachment, 1, false);
    }

    @Override
    public int getFluidAmount() {
        return (int) Math.min(handler.getFluidStored(attachment), Integer.MAX_VALUE);
    }

    @Override
    public int getCapacity() {
        return (int) Math.min(handler.getFluidCapacity(attachment), Integer.MAX_VALUE);
    }

    @Override
    public FluidTankInfo getInfo() {
        return new FluidTankInfo(this);
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource == null) return 0;
        return (int) Math.min(handler.fillFluid(attachment, resource, doFill), Integer.MAX_VALUE);
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return handler.drainFluid(attachment, maxDrain, doDrain);
    }
}
