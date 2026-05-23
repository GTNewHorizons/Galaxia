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
        List<StationModuleAlert> alerts = new ArrayList<>();
        for (StationModuleAlertProvider provider : PROVIDERS) {
            List<StationModuleAlert> provided = provider.alerts(facility, module);
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
        Map<ModuleInstance.ID, List<StationModuleAlert>> result = new LinkedHashMap<>();
        for (ModuleInstance module : facility.modules()) {
            List<StationModuleAlert> alerts = alertsFor(facility, module);
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
}
