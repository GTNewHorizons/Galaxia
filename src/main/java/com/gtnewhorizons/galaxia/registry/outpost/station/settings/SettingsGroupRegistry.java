package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.LayoutCacheBundle;
import com.gtnewhorizons.galaxia.registry.outpost.station.MutationKind;
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

    public void updateSettings(short groupId, ModuleSettings newSettings, LayoutCacheBundle cache) {
        SettingsGroup group = groups.get(groupId);
        if (group == null) return;
        ModuleSettings oldSettings = group.settings();
        group.setSettings(newSettings);
        if (!newSettings.equals(oldSettings)) {
            cache.applyMutation(MutationKind.SET_TIER, group.kind());
        }
    }
}
