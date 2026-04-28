package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import java.util.HashSet;
import java.util.Set;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class SettingsGroup {

    public final short id;
    public final FacilityModuleKind kind;
    public final Set<StationTileCoord> members;
    public ModuleSettings settings;

    public SettingsGroup(short id, FacilityModuleKind kind, ModuleSettings settings) {
        this.id = id;
        this.kind = kind;
        this.members = new HashSet<>();
        this.settings = settings;
    }
}
