package com.gtnewhorizons.galaxia.registry.outpost;

public sealed interface InventoryKey permits ItemStackWrapper,FluidKey {

    default String toKey() {
        return isItem() ? (((ItemStackWrapper) this).toKey())
            : ((FluidKey) this).fluid()
                .getName();
    }

    default boolean isItem() {
        return this instanceof ItemStackWrapper;
    }
}
