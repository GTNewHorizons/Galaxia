package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.MinorBodyOrbitSlot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;

public record AsteroidFieldNode(@Nonnull MinorCelestialBodyId id, @Nonnull CelestialObjectId beltId, int index,
    @Nonnull String displayName, @Nonnull AsteroidNodeKind kind, @Nonnull AsteroidSizeClass sizeClass,
    @Nonnull DiscoveryState initialDetectionState, @Nullable CelestialResourceKnowledgeState initialOreKnowledgeState,
    @Nonnull MinorBodyOrbitSlot orbitSlot, @Nonnull AsteroidOreProfile oreProfile,
    @Nonnull AsteroidAppearanceProfile appearance) {

    public AsteroidFieldNode(@Nonnull MinorCelestialBodyId id, @Nonnull CelestialObjectId beltId, int index,
        @Nonnull String displayName, @Nonnull AsteroidNodeKind kind, @Nonnull AsteroidSizeClass sizeClass,
        @Nonnull DiscoveryState initialDetectionState, double angleOffsetDeg, double orbitalDepth01,
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
            new MinorBodyOrbitSlot(angleOffsetDeg, orbitalDepth01),
            oreProfile,
            appearance);
    }

    public AsteroidFieldNode(@Nonnull MinorCelestialBodyId id, @Nonnull CelestialObjectId beltId, int index,
        @Nonnull String displayName, @Nonnull AsteroidNodeKind kind, @Nonnull AsteroidSizeClass sizeClass,
        @Nonnull DiscoveryState initialDetectionState,
        @Nullable CelestialResourceKnowledgeState initialOreKnowledgeState, double angleOffsetDeg,
        double orbitalDepth01, @Nonnull AsteroidOreProfile oreProfile, @Nonnull AsteroidAppearanceProfile appearance) {
        this(
            id,
            beltId,
            index,
            displayName,
            kind,
            sizeClass,
            initialDetectionState,
            initialOreKnowledgeState,
            new MinorBodyOrbitSlot(angleOffsetDeg, orbitalDepth01),
            oreProfile,
            appearance);
    }

    public AsteroidFieldNode {
        if (index < 0) {
            throw new IllegalArgumentException("node index must be non-negative");
        }
        if (!id.parentBodyId()
            .equals(beltId)) {
            throw new IllegalArgumentException("node id parent must match belt id");
        }
        if (id.index() != index) {
            throw new IllegalArgumentException("node id index must match node index");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        if (initialOreKnowledgeState != null && initialDetectionState == DiscoveryState.HIDDEN
            && initialOreKnowledgeState != CelestialResourceKnowledgeState.UNKNOWN) {
            throw new IllegalArgumentException("hidden asteroid nodes cannot expose ore knowledge");
        }
        if (orbitSlot == null) {
            throw new IllegalArgumentException("orbit slot is required");
        }
    }

    public double angleOffsetDeg() {
        return orbitSlot.angleOffsetDeg();
    }

    public double orbitalDepth01() {
        return orbitSlot.orbitalDepth01();
    }
}
