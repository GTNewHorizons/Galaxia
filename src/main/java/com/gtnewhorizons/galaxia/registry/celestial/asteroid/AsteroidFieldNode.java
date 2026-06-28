package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public record AsteroidFieldNode(MinorCelestialBodyId id, CelestialObjectId beltId, int index, String displayName,
    AsteroidNodeKind kind, AsteroidSizeClass sizeClass, double angleOffsetDeg, double orbitalDepth01,
    AsteroidOreProfile oreProfile, AsteroidAppearanceProfile appearance) {

    public AsteroidFieldNode {
        id = Objects.requireNonNull(id, "id cannot be null");
        beltId = Objects.requireNonNull(beltId, "beltId cannot be null");
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
        kind = Objects.requireNonNull(kind, "kind cannot be null");
        sizeClass = Objects.requireNonNull(sizeClass, "sizeClass cannot be null");
        if (!Double.isFinite(angleOffsetDeg) || angleOffsetDeg < 0.0 || angleOffsetDeg >= 360.0) {
            throw new IllegalArgumentException("angleOffsetDeg must be in [0, 360)");
        }
        if (!Double.isFinite(orbitalDepth01) || orbitalDepth01 < 0.0 || orbitalDepth01 > 1.0) {
            throw new IllegalArgumentException("orbitalDepth01 must be in [0, 1]");
        }
        oreProfile = Objects.requireNonNull(oreProfile, "oreProfile cannot be null");
        appearance = Objects.requireNonNull(appearance, "appearance cannot be null");
    }
}
