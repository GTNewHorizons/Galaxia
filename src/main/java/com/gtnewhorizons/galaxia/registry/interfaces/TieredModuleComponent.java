package com.gtnewhorizons.galaxia.registry.interfaces;

import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepDemand;

public abstract class TieredModuleComponent implements IModuleComponent {

    @Override
    public IModuleOperation prepareOperationTarget(ModuleInstance module, FacilityCommand.ModuleCommand request) {
        if (!(request instanceof FacilityCommand.PlanTierUpgrade plan)) {
            return IModuleComponent.super.prepareOperationTarget(module, request);
        }
        if (plan.targetTier() == null || plan.targetTier() == module.tier()
            || !module.kind()
                .allowedTiers()
                .contains(plan.targetTier())) {
            throw new IllegalArgumentException("Invalid tier operation target");
        }
        return new ModuleTierOperation(plan.targetTier());
    }

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
