package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public record AsteroidFieldNode(@Nonnull MinorCelestialBodyId id, @Nonnull CelestialObjectId beltId, int index,
    @Nonnull String displayName, @Nonnull AsteroidNodeKind kind, @Nonnull AsteroidSizeClass sizeClass,
    @Nonnull AsteroidDetectionState initialDetectionState,
    @Nullable AsteroidOreKnowledgeState initialOreKnowledgeState, double angleOffsetDeg, double orbitalDepth01,
    @Nonnull AsteroidOreProfile oreProfile, @Nonnull AsteroidAppearanceProfile appearance) {

    public AsteroidFieldNode(@Nonnull MinorCelestialBodyId id, @Nonnull CelestialObjectId beltId, int index,
        @Nonnull String displayName, @Nonnull AsteroidNodeKind kind, @Nonnull AsteroidSizeClass sizeClass,
        @Nonnull AsteroidDetectionState initialDetectionState, double angleOffsetDeg, double orbitalDepth01,
        @Nonnull AsteroidOreProfile oreProfile, @Nonnull AsteroidAppearanceProfile appearance) {
        this(
            id,
            beltId,
            index,
            displayName,
            kind,
            sizeClass,
            initialDetectionState,
            null,
            angleOffsetDeg,
            orbitalDepth01,
            oreProfile,
            appearance);
    }

    public AsteroidFieldNode {
        if (index < 0) {
            throw new IllegalArgumentException("node index must be non-negative");
        }
        if (!id.parentBeltId()
            .equals(beltId)) {
            throw new IllegalArgumentException("node id parent must match belt id");
        }
        if (id.index() != index) {
            throw new IllegalArgumentException("node id index must match node index");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        if (initialOreKnowledgeState != null && initialDetectionState == AsteroidDetectionState.HIDDEN
            && initialOreKnowledgeState != AsteroidOreKnowledgeState.UNKNOWN) {
            throw new IllegalArgumentException("hidden asteroid nodes cannot expose ore knowledge");
        }
        if (!Double.isFinite(angleOffsetDeg) || angleOffsetDeg < 0.0 || angleOffsetDeg >= 360.0) {
            throw new IllegalArgumentException("angleOffsetDeg must be in [0, 360)");
        }
        if (!Double.isFinite(orbitalDepth01) || orbitalDepth01 < 0.0 || orbitalDepth01 > 1.0) {
            throw new IllegalArgumentException("orbitalDepth01 must be in [0, 1]");
        }
    }
}
