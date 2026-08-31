package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.Map;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

public record FacilityModuleSettingsSnapshot(Map<ModuleInstance.ID, ModuleSettings> privateSettings,
    Map<SettingsGroup.ID, SettingsGroup> groups, Map<ModuleInstance.ID, SettingsGroup.ID> membership) {

    public FacilityModuleSettingsSnapshot {
        privateSettings = Map.copyOf(privateSettings);
        groups = Map.copyOf(groups);
        membership = Map.copyOf(membership);
    }
}
