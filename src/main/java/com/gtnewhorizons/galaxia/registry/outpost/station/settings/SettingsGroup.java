package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

public record SettingsGroup(ID id, FacilityModuleKind kind, String displayName, ModuleSettings settings) {

    public record ID(int value) {

        public ID {
            if (value <= 0) throw new IllegalArgumentException("Settings group ID must be positive: " + value);
        }
    }

    public SettingsGroup {
        if (id == null) throw new IllegalArgumentException("Settings group ID must not be null");
        if (kind == null) throw new IllegalArgumentException("Settings group kind must not be null for " + id);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Settings group name must not be blank for " + id);
        }
        if (settings == null) throw new IllegalArgumentException("Settings group settings must not be null for " + id);
        displayName = displayName.trim();
    }

    public SettingsGroup withDisplayName(String replacement) {
        return new SettingsGroup(id, kind, replacement, settings);
    }

    public SettingsGroup withSettings(ModuleSettings replacement) {
        return new SettingsGroup(id, kind, displayName, replacement);
    }
}
