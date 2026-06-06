package com.gtnewhorizons.galaxia.registry.interfaces;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepDemand;

public abstract class TieredModuleComponent implements IModuleComponent {

    @Override
    public void applyOperationTarget(IModuleOperation spec, ModuleInstance module) {
        if (spec instanceof ModuleTierOperation tierSpec) {
            module.setTier(tierSpec.targetTier());
            return;
        }
        throw new IllegalStateException(
            getClass().getSimpleName() + " does not support operation "
                + spec.getClass()
                    .getSimpleName());
    }

    @Override
    public UpkeepDemand upkeepFor(ModuleInstance module) {
        return module.currentTierUpkeepDemand();
    }
}
