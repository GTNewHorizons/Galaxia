package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class SettingsGroupRegistry {

    private final Map<Short, SettingsGroup> groups;
    private short nextGroupId;

    public SettingsGroupRegistry() {
        this.groups = new HashMap<>();
        this.nextGroupId = 1;
    }

    public Map<Short, SettingsGroup> groups() {
        return Collections.unmodifiableMap(groups);
    }

    public short nextGroupId() {
        return nextGroupId;
    }

    public void setNextGroupId(short nextGroupId) {
        this.nextGroupId = nextGroupId;
    }

    public SettingsGroup create(FacilityModuleKind kind, ModuleSettings settings) {
        if (nextGroupId == Short.MAX_VALUE) {
            throw new IllegalStateException("SettingsGroup ID space exhausted");
        }
        short id = nextGroupId++;
        SettingsGroup group = new SettingsGroup(id, kind, settings);
        groups.put(id, group);
        return group;
    }

    public boolean delete(short groupId) {
        return groups.remove(groupId) != null;
    }

    public boolean addMember(short groupId, StationTileCoord coord) {
        SettingsGroup group = groups.get(groupId);
        if (group == null) return false;
        group.addMember(coord);
        return true;
    }

    public boolean removeMember(short groupId, StationTileCoord coord) {
        SettingsGroup group = groups.get(groupId);
        if (group == null) return false;
        group.removeMember(coord);
        return true;
    }

    /**
     * Skeleton — Phase 8 (T8.2) wires the diff-into-atomic-MutationKinds batch path.
     */
    public void updateSettings(short groupId, ModuleSettings newSettings) {
        SettingsGroup group = groups.get(groupId);
        if (group == null) return;
        group.setSettings(newSettings);
    }
}
