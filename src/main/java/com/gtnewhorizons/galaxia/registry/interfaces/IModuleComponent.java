package com.gtnewhorizons.galaxia.registry.interfaces;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;

public interface IModuleComponent {

    default void applyOperationTarget(IModuleOperation spec, ModuleInstance module) {
        throw new IllegalStateException(
            getClass().getSimpleName() + " does not support operation "
                + spec.getClass()
                    .getSimpleName());
    }

    default int cooldownTicks(ModuleInstance module, ModuleTierData data) {
        return data.cooldownTicks();
    }

    default void tickOperational(ModuleInstance module, AutomatedFacility outpost) {}
}
