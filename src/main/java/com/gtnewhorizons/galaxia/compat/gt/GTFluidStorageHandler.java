package com.gtnewhorizons.galaxia.compat.gt;

import com.gtnewhorizons.galaxia.registry.interfaces.IFluidStorageHandler;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

public abstract class GTFluidStorageHandler<T extends MTEMultiBlockBase> extends GTBaseAttachmentHandler<T>
    implements IFluidStorageHandler<T> {

    @Override
    public ResourceFilter<FluidKey> getFluidFilter(T attachment) {
        var hatch = getHatch(attachment);
        if (hatch == null) return ResourceFilter.forFluids();
        return hatch.getFluidFilter();
    }

    public void setFluidFilter(T attachment, ResourceFilter<FluidKey> filter) {
        var hatch = getHatch(attachment);
        if (hatch == null) return;
        hatch.setFluidFilter(filter);
    }

    public int getPriority(T attachment) {
        var hatch = getHatch(attachment);
        if (hatch == null) return 0;
        return hatch.getPriority();
    }

    public void setPriority(T attachment, int priority) {
        var hatch = getHatch(attachment);
        if (hatch == null) return;
        hatch.setPriority(priority);
    }
}
