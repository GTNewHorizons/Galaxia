package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.BlockingReason;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepLedger;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepSettlement;

public final class StationModuleAlertRegistry {

    private StationModuleAlertRegistry() {}

    public static List<StationModuleAlert> alertsFor(AutomatedFacility facility, ModuleInstance module) {
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

    public static Map<ModuleInstance.ID, List<StationModuleAlert>> alerts(AutomatedFacility facility) {
        if (facility == null) return Map.of();
        Map<ModuleInstance.ID, List<StationModuleAlert>> result = new LinkedHashMap<>();
        for (ModuleInstance module : facility.modules()) {
            List<StationModuleAlert> alerts = alertsFor(facility, module);
            if (!alerts.isEmpty()) result.put(module.id, alerts);
        }
        return result.isEmpty() ? Map.of() : Collections.unmodifiableMap(result);
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
