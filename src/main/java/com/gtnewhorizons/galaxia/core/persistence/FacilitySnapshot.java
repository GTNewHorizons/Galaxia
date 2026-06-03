package com.gtnewhorizons.galaxia.core.persistence;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepSettlement;

record FacilitySnapshot(long energyStored, long stationFeatureSalt, short settingsGroupsNextId,
    List<SettingsGroup> settingsGroups, List<ModuleInstance> modules, Map<ItemStackWrapper, Long> itemBuffer,
    Map<String, Long> fluidBuffer, UpkeepSettlement.Credits upkeepCredits, StationLayout layout) {

    static FacilitySnapshot from(AutomatedFacility state) {
        state.syncRecipeSettingsGroupsFromModules();
        return new FacilitySnapshot(
            state.getEnergyStored(),
            state.stationFeatureSalt(),
            state.settingsGroups()
                .nextGroupId(),
            sortedSettingsGroups(state),
            List.copyOf(state.modules()),
            new LinkedHashMap<>(state.itemSnapshot()),
            new LinkedHashMap<>(state.fluidSnapshot()),
            state.upkeepCredits(),
            state.stationLayout());
    }

    private static List<SettingsGroup> sortedSettingsGroups(AutomatedFacility state) {
        return state.settingsGroups()
            .groups()
            .values()
            .stream()
            .sorted(Comparator.comparingInt(SettingsGroup::id))
            .toList();
    }
}
