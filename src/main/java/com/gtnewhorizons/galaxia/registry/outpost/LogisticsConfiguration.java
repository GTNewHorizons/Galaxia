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

    private final Map<InventoryKey, LogisticsResourceConfig> configs = new LinkedHashMap<>();

    /** Returns the config for a resource, or {@link LogisticsResourceConfig#DEFAULT} if absent. */
    public LogisticsResourceConfig get(InventoryKey key) {
        LogisticsResourceConfig cfg = configs.get(key);
        return cfg != null ? cfg : LogisticsResourceConfig.DEFAULT;
    }

    /** Sets (or replaces) the config for a resource. */
    public void set(InventoryKey key, LogisticsResourceConfig config) {
        if (config == null) {
            configs.remove(key);
        } else {
            configs.put(key, config);
        }
    }

    /** Removes any explicit config for the resource, reverting it to DEFAULT. */
    public void reset(InventoryKey item) {
        configs.remove(item);
    }

    /** Returns an unmodifiable snapshot of all explicitly configured resources. */
    public Map<InventoryKey, LogisticsResourceConfig> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(configs));
    }

    /** Replaces all configs from a deserialized snapshot or migration. */
    public void loadFromSnapshot(Map<InventoryKey, LogisticsResourceConfig> snapshot) {
        configs.clear();
        configs.putAll(snapshot);
    }

    public void clear() {
        configs.clear();
    }
}
