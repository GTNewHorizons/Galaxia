package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

final class AsteroidStarmapProjectionBuilderTest {

    @Test
    void decorateUsesCanonicalBodiesWithoutReenumeratingMembership() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode large = node(knowledge, AsteroidSizeClass.LARGE);
        AsteroidFieldNode medium = node(knowledge, AsteroidSizeClass.MEDIUM);

        knowledge.revealDiscovery(
            new AsteroidFieldDiscoveryWork(
                CelestialObjectKey.minorBody(medium.id()),
                com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep.DETECTION),
            new AsteroidFieldScanContext(node -> true, AsteroidFieldScanOrder.byIndex()));

        CelestialObject belt = belt(profile());
        List<CelestialObject> canonical = List.of(materialize(large), materialize(medium));
        List<AsteroidStarmapProjection> projections = AsteroidStarmapProjectionBuilder.decorate(
            belt,
            canonical,
            Optional.of(knowledge.snapshot(CelestialObjectId.FROZEN_BELT)),
            false,
            Set.of(),
            Set.of());

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
    }

    @Test
    void debugModeMarksHiddenCanonicalBodiesAsDebugHidden() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode hidden = node(knowledge, AsteroidSizeClass.SMALL);

        List<AsteroidStarmapProjection> projections = AsteroidStarmapProjectionBuilder.decorate(
            belt(profile()),
            List.of(materialize(hidden)),
            Optional.of(knowledge.snapshot(CelestialObjectId.FROZEN_BELT)),
            true,
            Set.of(),
            Set.of());

        AsteroidStarmapProjection hiddenProjection = projections.get(0);
        assertEquals(DiscoveryState.HIDDEN, hiddenProjection.detectionState());
        assertTrue(hiddenProjection.debugHidden());
        assertFalse(hiddenProjection.canShowOreDetails());
        assertEquals(Optional.empty(), hiddenProjection.visibleOreProfileId());
        assertEquals(List.of(), hiddenProjection.visibleGtOreVeinIds());
    }

    @Test
    void activeDiscoveryScanMarksHiddenCanonicalBodyAsScanningGhost() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode hidden = node(knowledge, AsteroidSizeClass.SMALL);

        List<AsteroidStarmapProjection> projections = AsteroidStarmapProjectionBuilder.decorate(
            belt(profile()),
            List.of(materialize(hidden)),
            Optional.of(knowledge.snapshot(CelestialObjectId.FROZEN_BELT)),
            false,
            Set.of(hidden.id()),
            Set.of());

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
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode hidden = node(knowledge, AsteroidSizeClass.SMALL);

        List<AsteroidStarmapProjection> projections = AsteroidStarmapProjectionBuilder.decorate(
            belt(profile()),
            List.of(materialize(hidden)),
            Optional.of(knowledge.snapshot(CelestialObjectId.FROZEN_BELT)),
            false,
            Set.of(),
            Set.of(hidden.id()));

        AsteroidStarmapProjection hiddenProjection = projections.get(0);
        assertEquals(DiscoveryState.HIDDEN, hiddenProjection.detectionState());
        assertTrue(hiddenProjection.sensorRevealed());
        assertFalse(hiddenProjection.scanInProgress());
        assertFalse(hiddenProjection.debugHidden());
        assertFalse(hiddenProjection.canShowOreDetails());
    }

    @Test
    void oreDetailsFollowTeamKnowledgeLevel() {
        AsteroidFieldKnowledgeSnapshot snapshot = new AsteroidFieldKnowledgeSnapshot(
            CelestialObjectId.FROZEN_BELT,
            List.of(
                new AsteroidFieldKnowledgeSnapshot.Entry(
                    AsteroidSlotRanges.GENERATED_SLOT_MIN,
                    DiscoveryState.DISCOVERED,
                    CelestialResourceKnowledgeState.UNKNOWN),
                new AsteroidFieldKnowledgeSnapshot.Entry(
                    AsteroidSlotRanges.GENERATED_SLOT_MIN + 1,
                    DiscoveryState.DISCOVERED,
                    CelestialResourceKnowledgeState.SIGNATURE),
                new AsteroidFieldKnowledgeSnapshot.Entry(
                    AsteroidSlotRanges.GENERATED_SLOT_MIN + 2,
                    DiscoveryState.DISCOVERED,
                    CelestialResourceKnowledgeState.PROFILE)),
            List.of());

        AsteroidFieldProfile fieldProfile = profile();
        AsteroidFieldNodeCatalog catalog = AsteroidFieldNodeCatalog
            .fromGenerated(CelestialObjectId.FROZEN_BELT, fieldProfile);
        List<CelestialObject> canonical = catalog.nodes()
            .stream()
            .filter(
                node -> node.index() >= AsteroidSlotRanges.GENERATED_SLOT_MIN
                    && node.index() <= AsteroidSlotRanges.GENERATED_SLOT_MIN + 2)
            .map(node -> AsteroidCelestialMaterializer.materialize(node, fieldProfile))
            .toList();

        List<AsteroidStarmapProjection> projections = AsteroidStarmapProjectionBuilder
            .decorate(belt(fieldProfile), canonical, Optional.of(snapshot), false, Set.of(), Set.of());

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
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidStarmapProjection large = projection(knowledge, AsteroidSizeClass.LARGE);
        AsteroidStarmapProjection medium = projection(knowledge, AsteroidSizeClass.MEDIUM);
        AsteroidStarmapProjection small = projection(knowledge, AsteroidSizeClass.SMALL);

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
    void rejectsKnowledgeSnapshotForDifferentBelt() {
        AsteroidFieldKnowledgeSnapshot wrongBelt = new AsteroidFieldKnowledgeSnapshot(
            CelestialObjectId.MOON,
            List.of(),
            List.of());

        assertThrows(
            IllegalArgumentException.class,
            () -> AsteroidStarmapProjectionBuilder
                .decorate(belt(profile()), List.of(), Optional.of(wrongBelt), false, Set.of(), Set.of()));
    }

    private static AsteroidStarmapProjection projection(AsteroidFieldKnowledge knowledge, AsteroidSizeClass sizeClass) {
        AsteroidFieldNode idNode = node(knowledge, sizeClass);
        return AsteroidStarmapProjectionBuilder
            .decorate(
                belt(profile()),
                List.of(materialize(idNode)),
                Optional.of(knowledge.snapshot(CelestialObjectId.FROZEN_BELT)),
                true,
                Set.of(),
                Set.of())
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

    private static AsteroidFieldNode node(AsteroidFieldKnowledge knowledge, AsteroidSizeClass sizeClass) {
        return knowledge.nodes()
            .stream()
            .filter(node -> node.sizeClass() == sizeClass)
            .findFirst()
            .orElseThrow();
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
