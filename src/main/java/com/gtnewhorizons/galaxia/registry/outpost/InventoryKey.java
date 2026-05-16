package com.gtnewhorizons.galaxia.registry.outpost;

public sealed interface InventoryKey permits ItemStackWrapper,FluidKey {

    default String toKey() {
        return this instanceof ItemStackWrapper ? (((ItemStackWrapper) this).toKey())
            : ((FluidKey) this).fluid()
                .getName();
    }

}
