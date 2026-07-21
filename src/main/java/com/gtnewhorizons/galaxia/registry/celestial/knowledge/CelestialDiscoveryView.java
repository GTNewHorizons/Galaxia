package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.Optional;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

public interface CelestialDiscoveryView {

    Optional<DiscoveryState> discoveryState(@Nonnull CelestialObjectKey key);

    /**
     * Whether {@code key} is visible for map/GUI child lists.
     * Uses synced discovery when present; otherwise falls back to {@code initialState}.
     * <p>
     * Client adapters may override this for temporary scan/sensor ghosts without
     * falsifying {@link #discoveryState(CelestialObjectKey)}.
     */
    default boolean isVisible(@Nonnull CelestialObjectKey key, @Nonnull DiscoveryState initialState) {
        DiscoveryState state = discoveryState(key).orElse(initialState);
        return state == DiscoveryState.DISCOVERED;
    }

    static CelestialDiscoveryView empty() {
        return key -> Optional.empty();
    }
}
