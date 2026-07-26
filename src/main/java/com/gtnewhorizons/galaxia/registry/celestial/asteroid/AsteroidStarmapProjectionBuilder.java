package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;

/**
 * Decorates canonical asteroid bodies already returned by Registry children.
 * Does not enumerate the field catalog or decide child-list membership.
 */
public final class AsteroidStarmapProjectionBuilder {

    private AsteroidStarmapProjectionBuilder() {}

    public static List<AsteroidStarmapProjection> decorate(@Nonnull CelestialObject belt,
        @Nonnull List<CelestialObject> canonicalBodies, boolean includeHidden,
        @Nonnull Set<MinorCelestialBodyId> scanTargets, @Nonnull Set<MinorCelestialBodyId> sensorRevealTargets) {

        if (!belt.id()
            .isRegistered()) {
            throw new IllegalArgumentException("Asteroid starmap projection requires a registered belt body");
        }
        CelestialObjectId beltId = belt.id()
            .registeredBodyId();
        AsteroidFieldProfile profile = belt.properties()
            .asteroidFieldProfile();
        if (profile == null) {
            throw new IllegalArgumentException("Asteroid starmap projection requires an asteroid field profile");
        }

        List<AsteroidStarmapProjection> projections = new ArrayList<>();
        for (CelestialObject body : canonicalBodies) {
            if (body == null || !body.id()
                .isMinorBody()) continue;
            MinorCelestialBodyId minorId = body.id()
                .minorBodyId();
            if (minorId.parentBodyId() != beltId) continue;
            Optional<AsteroidFieldNode> node = AsteroidFieldResolver.findNode(beltId, profile, minorId.index());
            if (node.isEmpty()) continue;
            projections.add(toProjection(body, node.get(), includeHidden, scanTargets, sensorRevealTargets));
        }
        return List.copyOf(projections);
    }

    private static AsteroidStarmapProjection toProjection(CelestialObject body, AsteroidFieldNode node,
        boolean includeHidden, Set<MinorCelestialBodyId> scanTargets, Set<MinorCelestialBodyId> sensorRevealTargets) {

        CelestialKnowledgeFacts facts = CelestialKnowledgeClientState.facts(CelestialObjectKey.minorBody(node.id()))
            .orElseGet(() -> CelestialKnowledgeFacts.of(node.initialDetectionState(), initialOreKnowledgeState(node)));
        DiscoveryState detectionState = facts.discoveryState();
        CelestialResourceKnowledgeState oreKnowledgeState = facts.resourceKnowledgeState();
        boolean scanInProgress = detectionState == DiscoveryState.HIDDEN && scanTargets.contains(node.id());
        boolean sensorRevealed = detectionState == DiscoveryState.HIDDEN && !scanInProgress
            && sensorRevealTargets.contains(node.id());

        Optional<String> visibleOreProfileId = oreKnowledgeState == CelestialResourceKnowledgeState.UNKNOWN
            ? Optional.empty()
            : Optional.of(
                node.oreProfile()
                    .id());
        List<String> visibleGtOreVeinIds = oreKnowledgeState == CelestialResourceKnowledgeState.PROFILE
            ? node.oreProfile()
                .gtOreVeinIds()
            : List.of();

        return new AsteroidStarmapProjection(
            body,
            node.id(),
            node.kind(),
            node.sizeClass(),
            detectionState,
            oreKnowledgeState,
            visibleOreProfileId,
            visibleGtOreVeinIds,
            node.appearance(),
            detectionState == DiscoveryState.HIDDEN && includeHidden && !scanInProgress && !sensorRevealed,
            scanInProgress,
            sensorRevealed);
    }

    private static CelestialResourceKnowledgeState initialOreKnowledgeState(AsteroidFieldNode node) {
        return AsteroidFieldResolver.initialOreKnowledge(node);
    }
}
