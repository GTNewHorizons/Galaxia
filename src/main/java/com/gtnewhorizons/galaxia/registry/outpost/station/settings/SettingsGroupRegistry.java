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

    public void clear() {
        groups.clear();
        nextGroupId = 1;
    }

    public short nextGroupId() {
        return nextGroupId;
    }

    public void setNextGroupId(short nextGroupId) {
        if (nextGroupId <= 0) {
            throw new IllegalArgumentException("SettingsGroupRegistry: nextGroupId must be > 0, got " + nextGroupId);
        }
        this.nextGroupId = nextGroupId;
    }

    public SettingsGroup create(FacilityModuleKind kind, ModuleSettings settings) {
        return create(kind, null, settings);
    }

    public SettingsGroup create(FacilityModuleKind kind, String displayName, ModuleSettings settings) {
        if (kind == null) {
            throw new IllegalArgumentException("SettingsGroupRegistry.create: kind must not be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException(
                "SettingsGroupRegistry.create: settings must not be null for kind " + kind);
        }
        if (nextGroupId <= 0) {
            throw new IllegalStateException("SettingsGroupRegistry.create: invalid nextGroupId " + nextGroupId);
        }
        if (nextGroupId == Short.MAX_VALUE) {
            throw new IllegalStateException("SettingsGroup ID space exhausted");
        }
        short id = nextGroupId++;
        SettingsGroup group = new SettingsGroup(id, kind, displayName, settings);
        groups.put(id, group);
        return group;
    }

    public SettingsGroup restore(short id, FacilityModuleKind kind, String displayName, ModuleSettings settings) {
        if (groups.containsKey(id)) {
            throw new IllegalStateException("SettingsGroupRegistry.restore: duplicate groupId=" + id);
        }
        if (id >= nextGroupId) {
            throw new IllegalStateException(
                "SettingsGroupRegistry.restore: groupId=" + id + " is not below nextGroupId=" + nextGroupId);
        }
        SettingsGroup group = new SettingsGroup(id, kind, displayName, settings);
        groups.put(id, group);
        return group;
    }

    public SettingsGroup sync(short id, FacilityModuleKind kind, String displayName, ModuleSettings settings) {
        SettingsGroup existing = groups.get(id);
        if (existing != null) {
            if (existing.kind() != kind) {
                throw new IllegalStateException(
                    "SettingsGroupRegistry.sync: groupId=" + id
                        + " kind changed from "
                        + existing.kind()
                        + " to "
                        + kind);
            }
            existing.setDisplayName(displayName);
            existing.setSettings(settings);
            return existing;
        }
        SettingsGroup group = new SettingsGroup(id, kind, displayName, settings);
        groups.put(id, group);
        if (id >= nextGroupId) {
            if (id == Short.MAX_VALUE) {
                throw new IllegalStateException("SettingsGroup ID space exhausted");
            }
            nextGroupId = (short) (id + 1);
        }
        return group;
    }

    public SettingsGroup get(short groupId) {
        return groups.get(groupId);
    }

    public SettingsGroup require(short groupId) {
        SettingsGroup group = groups.get(groupId);
        if (group == null) {
            throw new IllegalStateException("SettingsGroupRegistry: missing group groupId=" + groupId);
        }
        return group;
    }

    public SettingsGroup require(short groupId, FacilityModuleKind expectedKind) {
        if (expectedKind == null) {
            throw new IllegalArgumentException("SettingsGroupRegistry.require: expectedKind must not be null");
        }
        SettingsGroup group = require(groupId);
        if (group.kind() != expectedKind) {
            throw new IllegalStateException(
                "SettingsGroupRegistry: wrong group kind for groupId=" + groupId
                    + ", expectedKind="
                    + expectedKind
                    + ", actualKind="
                    + group.kind());
        }
        return group;
    }

    public SettingsGroup delete(short groupId) {
        SettingsGroup removed = groups.remove(groupId);
        if (removed == null) {
            throw new IllegalStateException("SettingsGroupRegistry.delete: missing group groupId=" + groupId);
        }
        return removed;
    }

    public void rename(short groupId, String displayName) {
        SettingsGroup group = require(groupId);
        group.setDisplayName(displayName);
    }

    public void addMember(short groupId, StationTileCoord coord) {
        SettingsGroup group = require(groupId);
        group.addMember(coord);
    }

    public boolean removeMember(short groupId, StationTileCoord coord) {
        SettingsGroup group = require(groupId);
        boolean groupBecameEmpty = group.removeMember(coord);
        if (groupBecameEmpty) {
            groups.remove(groupId);
        }
        return groupBecameEmpty;
    }

    /**
     * Skeleton - Phase 8 (T8.2) wires the diff-into-atomic-MutationKinds batch path.
     */
    public void updateSettings(short groupId, ModuleSettings newSettings) {
        SettingsGroup group = require(groupId);
        group.setSettings(newSettings);
    }
}
