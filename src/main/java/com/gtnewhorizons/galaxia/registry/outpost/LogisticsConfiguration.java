package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-outpost map of {@link LogisticsResourceConfig}, keyed by {@link ItemStackWrapper}.
 * Resources without an explicit entry inherit {@link LogisticsResourceConfig#DEFAULT}.
 *
 * <p>
 * Mutable; accessed only from the server thread.
 */
public final class LogisticsConfiguration {

    private final Map<ItemStackWrapper, LogisticsResourceConfig> configs = new LinkedHashMap<>();

    /** Returns the config for a resource, or {@link LogisticsResourceConfig#DEFAULT} if absent. */
    public LogisticsResourceConfig get(ItemStackWrapper item) {
        LogisticsResourceConfig cfg = configs.get(item);
        return cfg != null ? cfg : LogisticsResourceConfig.DEFAULT;
    }

    /** Sets (or replaces) the config for a resource. */
    public void set(ItemStackWrapper item, LogisticsResourceConfig config) {
        if (config == null) {
            configs.remove(item);
        } else {
            configs.put(item, config);
        }
    }

    /** Removes any explicit config for the resource, reverting it to DEFAULT. */
    public void reset(ItemStackWrapper item) {
        configs.remove(item);
    }

    /** Returns an unmodifiable snapshot of all explicitly configured resources. */
    public Map<ItemStackWrapper, LogisticsResourceConfig> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(configs));
    }

    /** Replaces all configs from a deserialized snapshot or migration. */
    public void loadFromSnapshot(Map<ItemStackWrapper, LogisticsResourceConfig> snapshot) {
        configs.clear();
        configs.putAll(snapshot);
    }

    public void clear() {
        configs.clear();
    }
}
