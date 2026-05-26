package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.List;

import net.minecraftforge.fluids.FluidStack;

public interface IFluidStorageHandler<T> extends IAttachmentHandler<T> {

    long getFluidStored(T attachment);

    long getFluidCapacity(T attachment);

    /**
     * @return all currently stored fluid stacks (non-null entries), or empty list if none
     */
    List<FluidStack> getAllFluids(T attachment);

    FluidStack drainFluid(T attachment, long amount, boolean doDrain);

    long fillFluid(T attachment, FluidStack resource, boolean doFill);
}
