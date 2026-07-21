package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

final class AsteroidStarmapProjectionBuilderTest {

    @AfterEach
    void clearClientState() {
        CelestialKnowledgeClientState.clear();
        AsteroidFieldClientCatalogState.clear();
    }

    @Test
    void decorateUsesCanonicalBodiesWithoutReenumeratingMembership() {
        AsteroidFieldProfile fieldProfile = profile();
        AsteroidFieldNodeCatalog catalog = seedCatalog(fieldProfile);
        AsteroidFieldNode large = node(catalog, AsteroidSizeClass.LARGE);
        AsteroidFieldNode medium = node(catalog, AsteroidSizeClass.MEDIUM);
        CelestialKnowledgeClientState
            .apply(Map.of(CelestialObjectKey.minorBody(medium.id()), CelestialKnowledgeFacts.discoveredUnknown()));

        List<AsteroidStarmapProjection> projections = AsteroidStarmapProjectionBuilder
            .decorate(belt(fieldProfile), List.of(materialize(large), materialize(medium)), false, Set.of(), Set.of());

        assertEquals(List.of(large.id(), medium.id()), projectionIds(projections));
        AsteroidStarmapProjection mediumProjection = projections.get(1);
        assertEquals(
            CelestialObjectKey.minorBody(medium.id()),
            mediumProjection.body()
                .id());
        assertEquals(
            CelestialObject.Class.ASTEROID,
            mediumProjection.body()
                .objectClass());
        assertEquals(medium.kind(), mediumProjection.nodeKind());
        assertEquals(medium.sizeClass(), mediumProjection.sizeClass());
        assertFalse(mediumProjection.debugHidden());
        assertEquals(DiscoveryState.DISCOVERED, mediumProjection.detectionState());
    }

    @Test
    void debugModeMarksHiddenCanonicalBodiesAsDebugHidden() {
        AsteroidFieldProfile fieldProfile = profile();
        AsteroidFieldNodeCatalog catalog = seedCatalog(fieldProfile);
        AsteroidFieldNode hidden = forceHidden(catalog, AsteroidSizeClass.SMALL);

        List<AsteroidStarmapProjection> projections = AsteroidStarmapProjectionBuilder
            .decorate(belt(fieldProfile), List.of(materialize(hidden)), true, Set.of(), Set.of());

        AsteroidStarmapProjection hiddenProjection = projections.get(0);
        assertEquals(DiscoveryState.HIDDEN, hiddenProjection.detectionState());
        assertTrue(hiddenProjection.debugHidden());
        assertFalse(hiddenProjection.canShowOreDetails());
        assertEquals(Optional.empty(), hiddenProjection.visibleOreProfileId());
        assertEquals(List.of(), hiddenProjection.visibleGtOreVeinIds());
    }

    @Test
    void activeDiscoveryScanMarksHiddenCanonicalBodyAsScanningGhost() {
        AsteroidFieldProfile fieldProfile = profile();
        AsteroidFieldNodeCatalog catalog = seedCatalog(fieldProfile);
        AsteroidFieldNode hidden = forceHidden(catalog, AsteroidSizeClass.SMALL);

        List<AsteroidStarmapProjection> projections = AsteroidStarmapProjectionBuilder
            .decorate(belt(fieldProfile), List.of(materialize(hidden)), false, Set.of(hidden.id()), Set.of());

        AsteroidStarmapProjection hiddenProjection = projections.get(0);
        assertEquals(DiscoveryState.HIDDEN, hiddenProjection.detectionState());
        assertTrue(hiddenProjection.scanInProgress());
        assertFalse(hiddenProjection.debugHidden());
        assertFalse(hiddenProjection.canShowOreDetails());
        assertEquals(Optional.empty(), hiddenProjection.visibleOreProfileId());
        assertEquals(List.of(), hiddenProjection.visibleGtOreVeinIds());
    }

    @Test
    void sensorRevealMarksHiddenCanonicalBodyWithoutActiveScan() {
        AsteroidFieldProfile fieldProfile = profile();
        AsteroidFieldNodeCatalog catalog = seedCatalog(fieldProfile);
        AsteroidFieldNode hidden = forceHidden(catalog, AsteroidSizeClass.SMALL);

        List<AsteroidStarmapProjection> projections = AsteroidStarmapProjectionBuilder
            .decorate(belt(fieldProfile), List.of(materialize(hidden)), false, Set.of(), Set.of(hidden.id()));

        AsteroidStarmapProjection hiddenProjection = projections.get(0);
        assertEquals(DiscoveryState.HIDDEN, hiddenProjection.detectionState());
        assertTrue(hiddenProjection.sensorRevealed());
        assertFalse(hiddenProjection.scanInProgress());
        assertFalse(hiddenProjection.debugHidden());
        assertFalse(hiddenProjection.canShowOreDetails());
    }

    @Test
    void oreDetailsFollowTeamKnowledgeLevel() {
        AsteroidFieldProfile fieldProfile = profile();
        AsteroidFieldNodeCatalog catalog = seedCatalog(fieldProfile);
        List<AsteroidFieldNode> nodes = catalog.nodes()
            .stream()
            .filter(
                node -> node.index() >= AsteroidSlotRanges.GENERATED_SLOT_MIN
                    && node.index() <= AsteroidSlotRanges.GENERATED_SLOT_MIN + 2)
            .toList();
        Map<CelestialObjectKey, CelestialKnowledgeFacts> facts = new LinkedHashMap<>();
        facts.put(
            CelestialObjectKey.minorBody(
                nodes.get(0)
                    .id()),
            CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.UNKNOWN));
        facts.put(
            CelestialObjectKey.minorBody(
                nodes.get(1)
                    .id()),
            CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.SIGNATURE));
        facts.put(
            CelestialObjectKey.minorBody(
                nodes.get(2)
                    .id()),
            CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.PROFILE));
        CelestialKnowledgeClientState.apply(facts);

        List<CelestialObject> canonical = nodes.stream()
            .map(node -> AsteroidCelestialMaterializer.materialize(node, fieldProfile))
            .toList();
        List<AsteroidStarmapProjection> projections = AsteroidStarmapProjectionBuilder
            .decorate(belt(fieldProfile), canonical, false, Set.of(), Set.of());

        assertEquals(
            Optional.empty(),
            projections.get(0)
                .visibleOreProfileId());
        assertEquals(
            List.of(),
            projections.get(0)
                .visibleGtOreVeinIds());
        assertFalse(
            projections.get(0)
                .canShowOreDetails());
        assertEquals(
            Optional.of("volatile_ice"),
            projections.get(1)
                .visibleOreProfileId());
        assertEquals(
            List.of(),
            projections.get(1)
                .visibleGtOreVeinIds());
        assertFalse(
            projections.get(1)
                .canShowOreDetails());
        assertEquals(
            Optional.of("volatile_ice"),
            projections.get(2)
                .visibleOreProfileId());
        assertEquals(
            List.of("ice", "sulfur"),
            projections.get(2)
                .visibleGtOreVeinIds());
        assertTrue(
            projections.get(2)
                .canShowOreDetails());
    }

    @Test
    void presentationHelpersPreferLargeAsteroidsAndLabelAuthoredAsteroids() {
        AsteroidFieldProfile fieldProfile = profile();
        seedCatalog(fieldProfile);
        AsteroidStarmapProjection large = projection(AsteroidSizeClass.LARGE);
        AsteroidStarmapProjection medium = projection(AsteroidSizeClass.MEDIUM);
        AsteroidStarmapProjection small = projection(AsteroidSizeClass.SMALL);

        assertFalse(large.drawDefaultLabel());
        assertFalse(medium.drawDefaultLabel());
        assertFalse(small.drawDefaultLabel());
        assertTrue(projectionWithKind(large, AsteroidNodeKind.LORE).drawDefaultLabel());
        assertTrue(projectionWithKind(medium, AsteroidNodeKind.UNIQUE).drawDefaultLabel());
        assertTrue(large.presentationPriority() > medium.presentationPriority());
        assertTrue(medium.presentationPriority() > small.presentationPriority());
        assertFalse(large.shouldCullAtNaturalRadius(2.0f, 2.0f));
        assertTrue(large.shouldCullAtNaturalRadius(0.99f, 2.0f));
    }

    @Test
    void ignoresCanonicalBodiesOutsideDecoratedBelt() {
        AsteroidFieldProfile fieldProfile = profile();
        seedCatalog(fieldProfile);
        AsteroidFieldNode medium = node(
            AsteroidFieldClientCatalogState.catalog(CelestialObjectId.FROZEN_BELT, fieldProfile),
            AsteroidSizeClass.MEDIUM);
        CelestialObject foreign = CelestialObject.builder()
            .id(
                CelestialObjectKey
                    .minorBody(new MinorCelestialBodyId(CelestialObjectId.MOON, AsteroidSlotRanges.GENERATED_SLOT_MIN)))
            .name("Foreign")
            .objectClass(CelestialObject.Class.ASTEROID)
            .circularOrbit(1.0, 0.0001, 0.0)
            .spriteSize(0.01)
            .build();

        List<AsteroidStarmapProjection> projections = AsteroidStarmapProjectionBuilder
            .decorate(belt(fieldProfile), List.of(foreign, materialize(medium)), true, Set.of(), Set.of());

        assertEquals(List.of(medium.id()), projectionIds(projections));
    }

    private static AsteroidStarmapProjection projection(AsteroidSizeClass sizeClass) {
        AsteroidFieldNodeCatalog catalog = AsteroidFieldClientCatalogState
            .catalog(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode idNode = node(catalog, sizeClass);
        return AsteroidStarmapProjectionBuilder
            .decorate(belt(profile()), List.of(materialize(idNode)), true, Set.of(), Set.of())
            .get(0);
    }

    private static AsteroidStarmapProjection projectionWithKind(AsteroidStarmapProjection source,
        AsteroidNodeKind kind) {
        return new AsteroidStarmapProjection(
            source.body(),
            source.id(),
            kind,
            source.sizeClass(),
            source.detectionState(),
            source.oreKnowledgeState(),
            source.visibleOreProfileId(),
            source.visibleGtOreVeinIds(),
            source.appearanceProfile(),
            source.debugHidden(),
            source.scanInProgress(),
            source.sensorRevealed());
    }

    private static List<MinorCelestialBodyId> projectionIds(List<AsteroidStarmapProjection> projections) {
        return projections.stream()
            .map(AsteroidStarmapProjection::id)
            .toList();
    }

    private static AsteroidFieldNode forceHidden(AsteroidFieldNodeCatalog catalog, AsteroidSizeClass sizeClass) {
        AsteroidFieldNode node = node(catalog, sizeClass);
        CelestialKnowledgeClientState.apply(
            Map.of(
                CelestialObjectKey.minorBody(node.id()),
                CelestialKnowledgeFacts.of(DiscoveryState.HIDDEN, CelestialResourceKnowledgeState.UNKNOWN)));
        return node;
    }

    private static AsteroidFieldNode node(AsteroidFieldNodeCatalog catalog, AsteroidSizeClass sizeClass) {
        return catalog.nodes()
            .stream()
            .filter(node -> node.sizeClass() == sizeClass)
            .findFirst()
            .orElseThrow();
    }

    private static AsteroidFieldNodeCatalog seedCatalog(AsteroidFieldProfile fieldProfile) {
        AsteroidFieldNodeCatalog catalog = AsteroidFieldNodeCatalog
            .fromGenerated(CelestialObjectId.FROZEN_BELT, fieldProfile);
        AsteroidFieldClientCatalogState.update(Map.of(CelestialObjectId.FROZEN_BELT, catalog.snapshots()));
        return catalog;
    }

    private static CelestialObject materialize(AsteroidFieldNode node) {
        return AsteroidCelestialMaterializer.materialize(node, profile());
    }

    private static CelestialObject belt(AsteroidFieldProfile profile) {
        return CelestialObject.builder()
            .id(CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT))
            .name("Frozen Belt")
            .objectClass(CelestialObject.Class.ASTEROID_BELT)
            .circularOrbit(4.5, 0.00012, 0.0)
            .spriteSize(0.03)
            .properties(properties -> properties.asteroidFieldProfile(profile))
            .build();
    }

    private static AsteroidFieldProfile profile() {
        return AsteroidFieldProfile.builder()
            .seedSalt(31L)
            .generationVersion(1)
            .sizeCounts(1, 1, 1)
            .radialBand(1.20, 1.40)
            .placementConnectionRadius(1.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", List.of("ice", "sulfur")))
            .build();
    }
}
