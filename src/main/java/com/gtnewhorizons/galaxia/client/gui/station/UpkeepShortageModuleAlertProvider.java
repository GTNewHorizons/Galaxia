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
        if (module.blocking() == BlockingReason.UPKEEP_SHORTAGE) {
            return List.of(
                StationModuleAlert
                    .critical("Upkeep", "Missing upkeep resources.", EnumTextures.ICON_STATION_ALERT_ERROR.get()));
        }
        UpkeepLedger.ModuleDemand demand = demandFor(facility, module);
        if (demand == null) return List.of();
        UpkeepSettlement.Result result = UpkeepSettlement.preview(
            facility.upkeepSummary()
                .moduleDemands(),
            facility.upkeepCredits(),
            facility);
        return result.unpaidModuleIds()
            .contains(module.id)
                ? List.of(
                    StationModuleAlert
                        .warning("Upkeep", "Missing upkeep resources.", EnumTextures.ICON_STATION_ALERT_WARNING.get()))
                : List.of();
    }

    private static UpkeepLedger.ModuleDemand demandFor(AutomatedFacility facility, ModuleInstance module) {
        if (facility == null || module == null) return null;
        for (UpkeepLedger.ModuleDemand demand : facility.upkeepSummary()
            .moduleDemands()) {
            if (module.id.equals(demand.moduleId()) && !demand.demand()
                .isEmpty()) {
                return demand;
            }
        }
        return null;
    }
}
