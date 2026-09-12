package com.gtnewhorizons.galaxia.registry.interfaces;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;

public class TieredModuleComponent implements IModuleComponent {

    @Override
    public boolean canUpgradeTo(ModuleInstance module, ModuleTier targetTier, @Nullable HammerVariant targetVariant) {
        return targetVariant == null && targetTier != null
            && targetTier != module.tier()
            && module.kind()
                .allowedTiers()
                .contains(targetTier);
    }

    @Override
    public IModuleOperation prepareOperationTarget(ModuleInstance module, FacilityCommand.ModuleCommand request) {
        if (!(request instanceof FacilityCommand.PlanTierUpgrade plan)) {
            return IModuleComponent.super.prepareOperationTarget(module, request);
        }
        if (!canUpgradeTo(module, plan.targetTier(), null)) {
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
