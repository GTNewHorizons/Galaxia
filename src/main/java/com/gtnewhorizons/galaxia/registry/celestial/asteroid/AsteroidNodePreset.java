package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Objects;

public record AsteroidNodePreset(int index, AsteroidNodeKind kind, String contentId, String displayName,
    AsteroidSizeClass sizeClass, AsteroidDetectionState initialDetectionState,
    AsteroidOreKnowledgeState initialOreKnowledgeState, Double angleOffsetDeg, Double orbitalDepth01,
    String oreProfileId, AsteroidAppearanceProfile appearance) {

    public AsteroidNodePreset(int index, AsteroidNodeKind kind, String displayName,
        AsteroidDetectionState initialDetectionState) {
        this(
            index,
            kind,
            null,
            displayName,
            AsteroidSizeClass.MEDIUM,
            initialDetectionState,
            null,
            null,
            null,
            null,
            null);
    }

    public AsteroidNodePreset {
        if (index < 0) {
            throw new IllegalArgumentException("preset index must be non-negative");
        }
        kind = Objects.requireNonNull(kind, "kind cannot be null");
        if (contentId != null && contentId.isBlank()) {
            throw new IllegalArgumentException("contentId cannot be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        sizeClass = Objects.requireNonNull(sizeClass, "sizeClass cannot be null");
        initialDetectionState = Objects.requireNonNull(initialDetectionState, "initialDetectionState cannot be null");
        if (initialDetectionState == AsteroidDetectionState.HIDDEN && initialOreKnowledgeState != null
            && initialOreKnowledgeState != AsteroidOreKnowledgeState.UNKNOWN) {
            throw new IllegalArgumentException("hidden asteroid presets cannot expose ore knowledge");
        }
        if (angleOffsetDeg != null
            && (!Double.isFinite(angleOffsetDeg) || angleOffsetDeg < 0.0 || angleOffsetDeg >= 360.0)) {
            throw new IllegalArgumentException("angleOffsetDeg must be in [0, 360)");
        }
        if (orbitalDepth01 != null
            && (!Double.isFinite(orbitalDepth01) || orbitalDepth01 < 0.0 || orbitalDepth01 > 1.0)) {
            throw new IllegalArgumentException("orbitalDepth01 must be in [0, 1]");
        }
        if (oreProfileId != null && oreProfileId.isBlank()) {
            throw new IllegalArgumentException("oreProfileId cannot be blank");
        }
    }
}
