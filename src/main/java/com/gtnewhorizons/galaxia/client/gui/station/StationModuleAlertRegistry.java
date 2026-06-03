package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepLedger;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepSettlement;

public final class StationModuleAlertRegistry {

    private static final List<StationModuleAlertProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    static {
        register(UpkeepShortageModuleAlertProvider.INSTANCE);
    }

    private StationModuleAlertRegistry() {}

    public static Registration register(StationModuleAlertProvider provider) {
        Objects.requireNonNull(provider, "provider");
        PROVIDERS.add(provider);
        return () -> PROVIDERS.remove(provider);
    }

    public static List<StationModuleAlert> alertsFor(AutomatedFacility facility, ModuleInstance module) {
        if (facility == null || module == null) return List.of();
        return alertsFor(Context.create(facility), module);
    }

    private static List<StationModuleAlert> alertsFor(Context context, ModuleInstance module) {
        if (context == null || module == null) return List.of();
        List<StationModuleAlert> alerts = new ArrayList<>();
        for (StationModuleAlertProvider provider : PROVIDERS) {
            List<StationModuleAlert> provided = provider.alerts(context, module);
            if (provided == null || provided.isEmpty()) continue;
            for (StationModuleAlert alert : provided) {
                if (alert != null) alerts.add(alert);
            }
        }
        alerts.sort((a, b) -> Integer.compare(severityRank(b.severity()), severityRank(a.severity())));
        return alerts.isEmpty() ? List.of() : Collections.unmodifiableList(alerts);
    }

    public static Map<ModuleInstance.ID, List<StationModuleAlert>> alerts(AutomatedFacility facility) {
        if (facility == null) return Map.of();
        Context context = Context.create(facility);
        Map<ModuleInstance.ID, List<StationModuleAlert>> result = new LinkedHashMap<>();
        for (ModuleInstance module : facility.modules()) {
            List<StationModuleAlert> alerts = alertsFor(context, module);
            if (!alerts.isEmpty()) result.put(module.id, alerts);
        }
        return result.isEmpty() ? Map.of() : Collections.unmodifiableMap(result);
    }

    private static int severityRank(StationModuleAlert.Severity severity) {
        return switch (severity) {
            case RED -> 1;
            case YELLOW -> 0;
        };
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {

        @Override
        void close();
    }

    public static final class Context {

        private final AutomatedFacility facility;
        private final UpkeepLedger.UpkeepSummary upkeepSummary;
        private UpkeepSettlement.Result upkeepPreview;

        private Context(AutomatedFacility facility, UpkeepLedger.UpkeepSummary upkeepSummary) {
            this.facility = facility;
            this.upkeepSummary = upkeepSummary;
        }

        static Context create(AutomatedFacility facility) {
            return new Context(facility, facility.upkeepSummary());
        }

        public AutomatedFacility facility() {
            return facility;
        }

        public UpkeepLedger.UpkeepSummary upkeepSummary() {
            return upkeepSummary;
        }

        public UpkeepSettlement.Result upkeepPreview() {
            if (upkeepPreview == null) {
                upkeepPreview = UpkeepSettlement
                    .preview(upkeepSummary.moduleDemands(), facility.upkeepCredits(), facility);
            }
            return upkeepPreview;
        }
    }
}
