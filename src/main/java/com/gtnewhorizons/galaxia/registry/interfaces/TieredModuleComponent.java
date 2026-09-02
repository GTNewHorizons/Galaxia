package com.gtnewhorizons.galaxia.registry.interfaces;

import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;

public class TieredModuleComponent implements IModuleComponent {

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
        return new IModuleOperation.Tier(plan.targetTier());
    }

    @Override
    public void applyOperationTarget(IModuleOperation spec, ModuleInstance module) {
        if (spec instanceof IModuleOperation.Tier tierSpec) {
            module.setTier(tierSpec.targetTier());
            return;
        }
        throw new IllegalStateException(
            getClass().getSimpleName() + " does not support operation "
                + spec.getClass()
                    .getSimpleName());
    }

}
