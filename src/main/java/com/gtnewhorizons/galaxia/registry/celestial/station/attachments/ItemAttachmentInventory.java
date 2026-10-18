package com.gtnewhorizons.galaxia.registry.celestial.station.attachments;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IItemStorageHandler;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

public class ItemAttachmentInventory<T> implements IDistributedInventory {

    private final IItemStorageHandler<T> handler;
    private final T attachment;

    public ItemAttachmentInventory(IItemStorageHandler<T> handler, T attachment) {
        this.handler = handler;
        this.attachment = attachment;
    }

    @Override
    public Map<ItemStackWrapper, Long> getItemAmounts() {
        List<ItemStack> items = handler.getAllItems(attachment);
        if (items.isEmpty()) return Map.of();
        Map<ItemStackWrapper, Long> map = new Object2LongOpenHashMap<>();
        for (ItemStack it : items) {
            if (it != null && it.stackSize > 0) {
                map.merge(ItemStackWrapper.of(it), (long) it.stackSize, Long::sum);
            }
        }

        return map;
    }

    @Override
    public long totalItemSlots() {
        return handler.getItemSlots(attachment);
    }

    @Override
    public long totalItemCapacity() {
        return handler.getItemCapacity(attachment);
    }

    @Override
    public long getFreeItemSpace(ItemStackWrapper item) {
        if (!getItemFilter().test(item)) return 0L;
        return insertIntoOwnStorage(item, Integer.MAX_VALUE, true);
    }

    @Override
    public long insertIntoOwnStorage(ItemStackWrapper item, long target) {
        return insertIntoOwnStorage(item, target, false);
    }

    @Override
    public long insertIntoOwnStorage(ItemStackWrapper item, long target, boolean simulate) {
        int toFill = (int) Math.min(target, Integer.MAX_VALUE);
        long moved = Math.max(0, handler.insertItem(attachment, item.toStack(toFill), !simulate));
        if (!simulate && moved > 0) handler.markDirty(attachment);
        return moved;
    }

    @Override
    public long extractFromOwnStorage(ItemStackWrapper item, long target) {
        return extractFromOwnStorage(item, target, false);
    }

    @Override
    public long extractFromOwnStorage(ItemStackWrapper item, long target, boolean simulate) {
        ItemStack drained = handler
            .extractItem(attachment, item.toStack((int) Math.min(target, Integer.MAX_VALUE)), !simulate);
        if (!simulate && drained != null && drained.stackSize > 0) handler.markDirty(attachment);
        return drained != null ? drained.stackSize : 0;
    }

    @Override
    public ResourceFilter<ItemStackWrapper> getItemFilter() {
        return handler.getItemFilter(attachment);
    }
}
