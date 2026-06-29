package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Objects;

public record AsteroidNodePreset(int index, AsteroidNodeKind kind, String displayName,
    AsteroidDetectionState initialDetectionState) {

    public AsteroidNodePreset {
        if (index < 0) {
            throw new IllegalArgumentException("preset index must be non-negative");
        }
        kind = Objects.requireNonNull(kind, "kind cannot be null");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        initialDetectionState = Objects.requireNonNull(initialDetectionState, "initialDetectionState cannot be null");
    }
}
