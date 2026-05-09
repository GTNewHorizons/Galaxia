package com.gtnewhorizons.galaxia.registry.outpost.module;

import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;

/**
 * Implemented by module components that support parallel execution.
 * Modules without this interface have no parallel mechanic (e.g. MaintenanceBay).
 */
public interface IParallelModule extends IModuleComponent {

    byte getParallel();

    void setParallel(byte parallel);
}
