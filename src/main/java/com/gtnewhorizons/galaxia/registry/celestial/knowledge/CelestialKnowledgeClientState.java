package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

/**
 * Client-side read model for object knowledge synced from the server.
 *
 * Typed client providers feed this generic read model. The starmap can then ask
 * for discovery by key without knowing which domain system produced that state.
 */
public final class CelestialKnowledgeClientState {

    private static final Map<Class<?>, Map<CelestialObjectKey, DiscoveryState>> DISCOVERY_BY_SOURCE = new LinkedHashMap<>();

    private CelestialKnowledgeClientState() {}

    public static CelestialDiscoveryView discoveryView() {
        return CelestialKnowledgeClientState::discoveryState;
    }

    public static void updateDiscoverySource(Class<?> source, Map<CelestialObjectKey, DiscoveryState> states) {
        if (source == null) throw new IllegalArgumentException("source is required");
        Map<CelestialObjectKey, DiscoveryState> copy = new LinkedHashMap<>();
        if (states != null) {
            states.forEach((key, state) -> {
                if (key == null) throw new IllegalArgumentException("discovery key cannot be null");
                if (state == null) throw new IllegalArgumentException("discovery state cannot be null");
                copy.put(key, state);
            });
        }
        if (copy.isEmpty()) {
            DISCOVERY_BY_SOURCE.remove(source);
        } else {
            DISCOVERY_BY_SOURCE.put(source, Map.copyOf(copy));
        }
    }

    public static Optional<DiscoveryState> discoveryState(CelestialObjectKey key) {
        if (key == null) return Optional.empty();
        return DISCOVERY_BY_SOURCE.values()
            .stream()
            .map(source -> source.get(key))
            .filter(state -> state != null)
            .findFirst();
    }

    public static void clear() {
        DISCOVERY_BY_SOURCE.clear();
    }
}
