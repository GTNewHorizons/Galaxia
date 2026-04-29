package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class SettingsGroup {

    private final short id;
    private final FacilityModuleKind kind;
    private final Set<StationTileCoord> members;
    private ModuleSettings settings;

    public SettingsGroup(short id, FacilityModuleKind kind, ModuleSettings settings) {
        this.id = id;
        this.kind = kind;
        this.members = new HashSet<>();
        this.settings = settings;
    }

    public short id() {
        return id;
    }

    public FacilityModuleKind kind() {
        return kind;
    }

    public Set<StationTileCoord> members() {
        return Collections.unmodifiableSet(members);
    }

    public ModuleSettings settings() {
        return settings;
    }

    public void setSettings(ModuleSettings settings) {
        this.settings = settings;
    }

    void addMember(StationTileCoord coord) {
        members.add(coord);
    }

    void removeMember(StationTileCoord coord) {
        members.remove(coord);
    }
}
