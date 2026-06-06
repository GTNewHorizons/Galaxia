package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

public interface IItemStorageHandler<T> extends IAttachmentHandler<T> {

    // TODO: get/set priority, set filter

    @Override
    default boolean hasDistributedInventory() {
        return true;
    }

    long getItemStored(T attachment);

    long getItemCapacity(T attachment);

    long getItemSlots(T attachment);

    List<ItemStack> getAllItems(T attachment);

    ItemStack extractItem(T attachment, ItemStack resource, boolean doDrain);

    long insertItem(T attachment, ItemStack resource, boolean doDrain);

    ResourceFilter<ItemStackWrapper> getItemFilter(T attachment);
}
