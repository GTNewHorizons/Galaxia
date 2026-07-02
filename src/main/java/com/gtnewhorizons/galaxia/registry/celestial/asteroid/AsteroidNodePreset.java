package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record AsteroidNodePreset(int index, @Nonnull AsteroidNodeKind kind, @Nullable String contentId,
    @Nonnull String displayName, @Nonnull AsteroidSizeClass sizeClass,
    @Nonnull AsteroidDetectionState initialDetectionState, @Nullable AsteroidOreKnowledgeState initialOreKnowledgeState,
    @Nullable Double angleOffsetDeg, @Nullable Double orbitalDepth01, @Nullable String oreProfileId,
    @Nullable AsteroidAppearanceProfile appearance) {

    public AsteroidNodePreset(int index, @Nonnull AsteroidNodeKind kind, @Nonnull String displayName,
        @Nonnull AsteroidDetectionState initialDetectionState) {
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
        if (contentId != null && contentId.isBlank()) {
            throw new IllegalArgumentException("contentId cannot be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
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
