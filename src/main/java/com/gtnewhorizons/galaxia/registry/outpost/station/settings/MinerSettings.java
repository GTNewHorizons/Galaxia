package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;

public final class MinerSettings implements ModuleSettings {

    private final Set<String> blacklistedOreKeys;

    public MinerSettings() {
        this.blacklistedOreKeys = new LinkedHashSet<>();
    }

    public MinerSettings(@Nonnull Set<String> blacklistedOreKeys) {
        this();
        setBlacklistedOreKeys(blacklistedOreKeys);
    }

    public Set<String> blacklistedOreKeys() {
        return Collections.unmodifiableSet(blacklistedOreKeys);
    }

    public boolean isOreBlacklisted(String oreKey) {
        return blacklistedOreKeys.contains(requireOreKey(oreKey));
    }

    public boolean setOreBlacklisted(String oreKey, boolean blacklisted) {
        String key = requireOreKey(oreKey);
        return blacklisted ? blacklistedOreKeys.add(key) : blacklistedOreKeys.remove(key);
    }

    public void setBlacklistedOreKeys(@Nonnull Set<String> oreKeys) {
        blacklistedOreKeys.clear();
        for (String oreKey : oreKeys) {
            blacklistedOreKeys.add(requireOreKey(oreKey));
        }
    }

    public MinerSettings copy() {
        return new MinerSettings(blacklistedOreKeys);
    }

    @Override
    public void applyTo(ModuleInstance instance) {
        if (!(instance.component() instanceof ModuleMiner)) {
            throw new IllegalStateException("MinerSettings applied to non-miner module " + instance.id);
        }
        throw new UnsupportedOperationException("Miner settings are stored in station settings groups");
    }

    @Override
    public ModuleSettings from(ModuleInstance instance) {
        if (!(instance.component() instanceof ModuleMiner)) {
            throw new IllegalStateException("MinerSettings read from non-miner module " + instance.id);
        }
        throw new UnsupportedOperationException("Miner settings are stored in station settings groups");
    }

    public static String requireOreKey(String oreKey) {
        if (oreKey == null || oreKey.isBlank()) {
            throw new IllegalArgumentException("MinerSettings: ore key must not be null or blank");
        }
        return oreKey;
    }
}
