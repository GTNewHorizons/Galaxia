package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

public final class CelestialKnowledgeSyncRegistry {

    private static final List<CelestialKnowledgeSyncAdapter> ADAPTERS = new ArrayList<>();
    private static final Map<CelestialKnowledgeSyncType, CelestialKnowledgeSyncAdapter> ADAPTERS_BY_TYPE = new LinkedHashMap<>();

    private CelestialKnowledgeSyncRegistry() {}

    public static void register(@Nonnull CelestialKnowledgeSyncAdapter adapter) {
        if (adapter == null) throw new IllegalArgumentException("knowledge sync adapter is required");
        CelestialKnowledgeSyncType type = adapter.type();
        CelestialKnowledgeSyncAdapter existing = ADAPTERS_BY_TYPE.get(type);
        if (existing == adapter) return;
        if (existing != null) throw new IllegalStateException("Duplicate celestial knowledge sync type: " + type.id());
        ADAPTERS.add(adapter);
        ADAPTERS_BY_TYPE.put(type, adapter);
    }

    static List<CelestialKnowledgeSyncAdapter> adapters() {
        return List.copyOf(ADAPTERS);
    }

    static CelestialKnowledgeSyncAdapter require(CelestialKnowledgeSyncType type) {
        CelestialKnowledgeSyncAdapter adapter = ADAPTERS_BY_TYPE.get(type);
        if (adapter == null) throw new IllegalStateException("Unknown celestial knowledge sync type: " + type.id());
        return adapter;
    }

    static void resetForTesting() {
        ADAPTERS.clear();
        ADAPTERS_BY_TYPE.clear();
    }
}
