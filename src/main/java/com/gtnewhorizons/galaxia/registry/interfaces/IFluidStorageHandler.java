package com.gtnewhorizons.galaxia.registry.interfaces;

import net.minecraftforge.fluids.FluidStack;

public interface IFluidStorageHandler<T> extends IAttachmentHandler<T> {

    long getFluidStored(T attachment);

    long getFluidCapacity(T attachment);

    FluidStack drainFluid(T attachment, long amount, boolean doDrain);

    long fillFluid(T attachment, FluidStack resource, boolean doFill);
}
