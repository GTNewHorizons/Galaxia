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
    private String displayName;
    private ModuleSettings settings;

    public SettingsGroup(short id, FacilityModuleKind kind, ModuleSettings settings) {
        this(id, kind, null, settings);
    }

    public SettingsGroup(short id, FacilityModuleKind kind, String displayName, ModuleSettings settings) {
        if (id <= 0) {
            throw new IllegalArgumentException("SettingsGroup: id must be > 0, got " + id);
        }
        if (kind == null) {
            throw new IllegalArgumentException("SettingsGroup: kind must not be null for groupId=" + id);
        }
        this.id = id;
        this.kind = kind;
        this.members = new HashSet<>();
        this.displayName = displayName == null ? defaultDisplayName() : validatedDisplayName(displayName);
        setSettings(settings);
    }

    public short id() {
        return id;
    }

    public FacilityModuleKind kind() {
        return kind;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = validatedDisplayName(displayName);
    }

    public Set<StationTileCoord> members() {
        return Collections.unmodifiableSet(members);
    }

    public ModuleSettings settings() {
        return settings;
    }

    public void setSettings(ModuleSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException(
                "SettingsGroup: settings must not be null for groupId=" + id + ", kind=" + kind);
        }
        this.settings = settings;
    }

    void addMember(StationTileCoord coord) {
        if (coord == null) {
            throw new IllegalArgumentException(
                "SettingsGroup: member must not be null for groupId=" + id + ", kind=" + kind);
        }
        if (!members.add(coord)) {
            throw new IllegalStateException(
                "SettingsGroup: member already in group groupId=" + id + ", kind=" + kind + ", member=" + coord);
        }
    }

    boolean removeMember(StationTileCoord coord) {
        if (coord == null) {
            throw new IllegalArgumentException(
                "SettingsGroup: member must not be null for groupId=" + id + ", kind=" + kind);
        }
        if (!members.remove(coord)) {
            throw new IllegalStateException(
                "SettingsGroup: member not in group groupId=" + id + ", kind=" + kind + ", member=" + coord);
        }
        return members.isEmpty();
    }

    private String defaultDisplayName() {
        return kind.name() + " #" + id;
    }

    private String validatedDisplayName(String rawDisplayName) {
        if (rawDisplayName == null) {
            throw new IllegalArgumentException(
                "SettingsGroup: displayName must not be null for groupId=" + id + ", kind=" + kind);
        }
        String trimmedDisplayName = rawDisplayName.trim();
        if (trimmedDisplayName.isEmpty()) {
            throw new IllegalArgumentException(
                "SettingsGroup: displayName must not be blank for groupId=" + id + ", kind=" + kind);
        }
        return trimmedDisplayName;
    }
}
