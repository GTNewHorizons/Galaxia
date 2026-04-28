package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
}
