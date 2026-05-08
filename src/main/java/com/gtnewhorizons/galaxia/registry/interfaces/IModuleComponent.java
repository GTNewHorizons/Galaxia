package com.gtnewhorizons.galaxia.registry.interfaces;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;

public interface IModuleComponent {

    default void tickOperational(ModuleInstance module, AutomatedFacility outpost) {}

    default void applyOperationTarget(IModuleOperation spec, ModuleInstance module) {
        if (spec instanceof ModuleTierOperation tierSpec) {
            module.setTier(tierSpec.targetTier());
            return;
        }
        throw new IllegalStateException(
            getClass().getSimpleName() + " does not support operation "
                + spec.getClass()
                    .getSimpleName());
    }
}
