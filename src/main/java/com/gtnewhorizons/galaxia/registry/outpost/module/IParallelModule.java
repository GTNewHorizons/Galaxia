package com.gtnewhorizons.galaxia.registry.outpost.module;

/**
 * Implemented by module components that support parallel execution.
 * Modules without this interface have no parallel mechanic (e.g. MaintenanceBay).
 */
public interface IParallelModule {

    byte getParallel();

    void setParallel(byte parallel);
}
