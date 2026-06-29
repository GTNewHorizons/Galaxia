package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public record AsteroidFieldStarmapEntry(MinorCelestialBodyId id, CelestialObjectId beltId, int index,
    String displayName, AsteroidNodeKind nodeKind, AsteroidSizeClass sizeClass, double angleOffsetDeg,
    double orbitalDepth01, AsteroidOreKnowledgeState oreKnowledgeState, AsteroidAppearanceProfile appearanceProfile) {

    public AsteroidFieldStarmapEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(beltId, "beltId");
        if (index < 0) {
            throw new IllegalArgumentException("Asteroid starmap index must be non-negative");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Asteroid starmap display name is required");
        }
        Objects.requireNonNull(nodeKind, "nodeKind");
        Objects.requireNonNull(sizeClass, "sizeClass");
        if (!Double.isFinite(angleOffsetDeg)) {
            throw new IllegalArgumentException("Asteroid starmap angle offset must be finite");
        }
        if (!Double.isFinite(orbitalDepth01) || orbitalDepth01 < 0.0 || orbitalDepth01 > 1.0) {
            throw new IllegalArgumentException("Asteroid starmap orbital depth must be in range [0, 1]");
        }
        Objects.requireNonNull(oreKnowledgeState, "oreKnowledgeState");
        Objects.requireNonNull(appearanceProfile, "appearanceProfile");
    }

    static AsteroidFieldStarmapEntry from(AsteroidFieldNode node, AsteroidFieldKnowledge.Entry knowledge) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(knowledge, "knowledge");
        if (knowledge.detectionState() != AsteroidDetectionState.DETECTED) {
            throw new IllegalArgumentException("Hidden asteroids cannot be exposed on the starmap");
        }

        return new AsteroidFieldStarmapEntry(
            node.id(),
            node.beltId(),
            node.index(),
            node.displayName(),
            node.kind(),
            node.sizeClass(),
            node.angleOffsetDeg(),
            node.orbitalDepth01(),
            knowledge.oreKnowledgeState(),
            node.appearance());
    }
}
