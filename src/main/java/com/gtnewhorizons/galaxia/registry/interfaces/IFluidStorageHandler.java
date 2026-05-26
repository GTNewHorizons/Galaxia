package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

public interface IFluidStorageHandler<T> extends IAttachmentHandler<T> {

    // TODO: get/set priority, set filter

    @Override
    default boolean hasDistributedInventory() {
        return true;
    }

    long getFluidStored(T attachment);

    long getFluidCapacity(T attachment);

    /**
     * @return all currently stored fluid stacks (non-null entries), or empty list if none
     */
    List<FluidStack> getAllFluids(T attachment);

    FluidStack drainFluid(T attachment, FluidStack resource, boolean doDrain);

    long fillFluid(T attachment, FluidStack resource, boolean doFill);

    ResourceFilter<FluidKey> getFluidFilter(T attachment);
}
