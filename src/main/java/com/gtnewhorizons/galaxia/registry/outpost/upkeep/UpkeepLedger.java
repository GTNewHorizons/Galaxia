package com.gtnewhorizons.galaxia.registry.outpost.upkeep;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;

public final class UpkeepLedger {

    public @Nonnull UpkeepSummary summary(@Nonnull AutomatedFacility facility) {
        Objects.requireNonNull(facility, "facility");
        UpkeepDemand aggregate = UpkeepDemand.EMPTY;
        List<ModuleDemand> moduleDemands = new java.util.ArrayList<>();
        for (ModuleInstance module : facility.modules()) {
            if (!countsForUpkeep(module)) continue;
            UpkeepDemand baseDemand = module.component()
                .upkeepFor(module);
            UpkeepDemand demand = facility.effectiveUpkeepDemand(module, baseDemand);
            if (demand.isEmpty()) continue;
            aggregate = aggregate.plus(demand);
            moduleDemands.add(new ModuleDemand(module.id, module.kind(), module.priorityOverride(), demand));
        }
        if (aggregate.isEmpty() && moduleDemands.isEmpty()) return UpkeepSummary.EMPTY;
        return new UpkeepSummary(aggregate.itemsPerMinute(), aggregate.fluidsPerMinute(), moduleDemands);
    }

    private static boolean countsForUpkeep(ModuleInstance module) {
        return module != null && module.isOperational() && module.enabled() && module.component() != null;
    }

    public record ModuleDemand(@Nonnull ModuleInstance.ID moduleId, @Nonnull FacilityModuleKind kind,
        @Nonnull ModulePriority priority, @Nonnull UpkeepDemand demand) {

        public ModuleDemand {
            Objects.requireNonNull(moduleId, "moduleId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(priority, "priority");
            Objects.requireNonNull(demand, "demand");
        }
    }

    public record UpkeepSummary(@Nonnull Map<ItemStackWrapper, UpkeepAmount> itemsPerMinute,
        @Nonnull Map<FluidKey, UpkeepAmount> fluidsPerMinute, @Nonnull List<ModuleDemand> moduleDemands) {

        public static final UpkeepSummary EMPTY = new UpkeepSummary(Map.of(), Map.of(), List.of());

        public UpkeepSummary {
            itemsPerMinute = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(itemsPerMinute)));
            fluidsPerMinute = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(fluidsPerMinute)));
            moduleDemands = List.copyOf(Objects.requireNonNull(moduleDemands));
        }

        public boolean isEmpty() {
            return itemsPerMinute.isEmpty() && fluidsPerMinute.isEmpty() && moduleDemands.isEmpty();
        }
    }
}
