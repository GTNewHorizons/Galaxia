package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;

public final class MinerSettings implements ModuleSettings {

    private final Set<String> blacklistedOreKeys;

    public MinerSettings() {
        this.blacklistedOreKeys = Set.of();
    }

    public MinerSettings(@Nonnull Set<String> blacklistedOreKeys) {
        LinkedHashSet<String> validated = new LinkedHashSet<>();
        for (String oreKey : blacklistedOreKeys) validated.add(requireOreKey(oreKey));
        this.blacklistedOreKeys = Set.copyOf(validated);
    }

    public Set<String> blacklistedOreKeys() {
        return blacklistedOreKeys;
    }

    public boolean isOreBlacklisted(String oreKey) {
        return blacklistedOreKeys.contains(requireOreKey(oreKey));
    }

    public MinerSettings withOreBlacklisted(String oreKey, boolean blacklisted) {
        String key = requireOreKey(oreKey);
        LinkedHashSet<String> updated = new LinkedHashSet<>(blacklistedOreKeys);
        boolean changed = blacklisted ? updated.add(key) : updated.remove(key);
        return changed ? new MinerSettings(updated) : this;
    }

    public static String requireOreKey(String oreKey) {
        if (oreKey == null || oreKey.isBlank()) {
            throw new IllegalArgumentException("MinerSettings: ore key must not be null or blank");
        }
        return oreKey;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MinerSettings settings && blacklistedOreKeys.equals(settings.blacklistedOreKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blacklistedOreKeys);
    }
}
