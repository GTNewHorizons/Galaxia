package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.Map;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

public interface IBoundedInventory {

    boolean hasItemLowerBound(ItemStackWrapper item);

    boolean hasItemUpperBound(ItemStackWrapper item);

    long itemLowerBoundOrDefault(ItemStackWrapper item);

    long itemUpperBoundOrDefault(ItemStackWrapper item);

    @Nonnull
    Map<ItemStackWrapper, Long> itemLowerBoundsSnapshot();

    @Nonnull
    Map<ItemStackWrapper, Long> itemUpperBoundsSnapshot();
}
