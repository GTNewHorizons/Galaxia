package com.gtnewhorizons.galaxia.compat.gt;

import com.gtnewhorizons.galaxia.registry.interfaces.IItemStorageHandler;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

public abstract class GTItemStorageHandler<T extends MTEMultiBlockBase> extends GTBaseAttachmentHandler<T>
    implements IItemStorageHandler<T> {

    @Override
    public ResourceFilter<ItemStackWrapper> getItemFilter(T attachment) {
        var hatch = getHatch(attachment);
        if (hatch == null) return ResourceFilter.forItems();
        return hatch.getItemFilter();
    }

    public void setItemFilter(T attachment, ResourceFilter<ItemStackWrapper> filter) {
        var hatch = getHatch(attachment);
        if (hatch == null) return;
        hatch.setItemFilter(filter);
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
