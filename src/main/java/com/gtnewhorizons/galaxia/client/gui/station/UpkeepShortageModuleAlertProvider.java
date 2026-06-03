package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.BlockingReason;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepLedger;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepSettlement;

final class UpkeepShortageModuleAlertProvider implements StationModuleAlertProvider {

    static final UpkeepShortageModuleAlertProvider INSTANCE = new UpkeepShortageModuleAlertProvider();

    private UpkeepShortageModuleAlertProvider() {}

    @Override
    public List<StationModuleAlert> alerts(AutomatedFacility facility, ModuleInstance module) {
        if (facility == null || module == null) return List.of();
        if (module.blocking() == BlockingReason.UPKEEP_SHORTAGE) {
            return List.of(
                StationModuleAlert
                    .critical("Upkeep", "Missing upkeep resources.", EnumTextures.ICON_STATION_ALERT_ERROR.get()));
        }
        UpkeepLedger.UpkeepSummary summary = facility.upkeepSummary();
        UpkeepLedger.ModuleDemand demand = demandFor(summary, module);
        if (demand == null) return List.of();
        return UpkeepSettlement.preview(summary.moduleDemands(), facility.upkeepCredits(), facility)
            .unpaidModuleIds()
            .contains(module.id)
                ? List.of(
                    StationModuleAlert
                        .warning("Upkeep", "Missing upkeep resources.", EnumTextures.ICON_STATION_ALERT_WARNING.get()))
                : List.of();
    }

    private static UpkeepLedger.ModuleDemand demandFor(UpkeepLedger.UpkeepSummary summary, ModuleInstance module) {
        if (summary == null || module == null) return null;
        for (UpkeepLedger.ModuleDemand demand : summary.moduleDemands()) {
            if (module.id.equals(demand.moduleId()) && !demand.demand()
                .isEmpty()) {
                return demand;
            }
        }
        return null;
    }
}
