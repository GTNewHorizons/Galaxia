package com.gtnewhorizons.galaxia.rocketmodules;

/**
 * any module that can be stacked in one layer
 */
public interface IStackableModule {

    /**
     * maximum amount of modules being in one layer counting central
     * example: 7 = 1 central + 6 on the sides
     */
    int getMaxStackSize();

    default boolean canFormCluster() {
        return getMaxStackSize() > 1;
    }
}
