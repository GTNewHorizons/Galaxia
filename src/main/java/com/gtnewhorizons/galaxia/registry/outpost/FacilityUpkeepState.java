package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gtnewhorizons.galaxia.registry.outpost.module.BlockingReason;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleState;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepLedger;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepSettlement;

final class FacilityUpkeepState {

    private UpkeepSettlement.Credits credits = UpkeepSettlement.Credits.empty();

    UpkeepSettlement.Credits credits() {
        return credits;
    }

    void loadCredits(UpkeepSettlement.Credits credits) {
        this.credits = credits == null ? UpkeepSettlement.Credits.empty() : credits;
    }

    UpkeepSettlement.Result settle(UpkeepLedger.UpkeepSummary summary, List<ModuleInstance> modules,
        AutomatedFacility facility, DirtyModuleSink dirtyModuleSink) {
        UpkeepSettlement.Result result = UpkeepSettlement.settle(summary.moduleDemands(), credits, facility);
        credits = result.credits();
        Set<ModuleInstance.ID> demanded = new HashSet<>();
        for (UpkeepLedger.ModuleDemand demand : summary.moduleDemands()) {
            demanded.add(demand.moduleId());
        }
        Set<ModuleInstance.ID> paid = result.paidModuleIds();
        Set<ModuleInstance.ID> unpaid = new HashSet<>(result.unpaidModuleIds());
        for (ModuleInstance module : modules) {
            if (unpaid.contains(module.id)) {
                setModuleUpkeepBlocked(module, dirtyModuleSink);
            } else if (paid.contains(module.id) || !demanded.contains(module.id)) {
                clearModuleUpkeepBlocked(module, dirtyModuleSink);
            }
        }
        return result;
    }

    private static void setModuleUpkeepBlocked(ModuleInstance module, DirtyModuleSink dirtyModuleSink) {
        if (module.blocking() == BlockingReason.UPKEEP_SHORTAGE && module.state() == ModuleState.BLOCKED) return;
        module.setBlocking(BlockingReason.UPKEEP_SHORTAGE);
        module.setState(ModuleState.BLOCKED);
        dirtyModuleSink.markDirty(module.id);
    }

    private static void clearModuleUpkeepBlocked(ModuleInstance module, DirtyModuleSink dirtyModuleSink) {
        if (module.blocking() != BlockingReason.UPKEEP_SHORTAGE) return;
        module.setBlocking(BlockingReason.NONE);
        if (module.state() == ModuleState.BLOCKED) {
            module.setState(ModuleState.IDLE);
        }
        dirtyModuleSink.markDirty(module.id);
    }

    @FunctionalInterface
    interface DirtyModuleSink {

        void markDirty(ModuleInstance.ID moduleId);
    }
}
