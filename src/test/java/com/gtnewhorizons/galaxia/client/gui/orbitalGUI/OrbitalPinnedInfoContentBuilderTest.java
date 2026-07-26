package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.util.StatCollector;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class OrbitalPinnedInfoContentBuilderTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void asteroidOreSignatureDoesNotExposeVeinIdsWithoutProfileKnowledge() {
        CelestialObject frozenBelt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        AsteroidFieldProfile profile = frozenBelt.properties()
            .asteroidFieldProfile();
        AsteroidFieldNode node = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile)
            .stream()
            .filter(
                candidate -> !candidate.oreProfile()
                    .gtOreVeinIds()
                    .isEmpty())
            .findFirst()
            .orElseThrow();
        CelestialKnowledgeClientState.apply(
            Map.of(
                CelestialObjectKey.minorBody(node.id()),
                CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.UNKNOWN)));
        CelestialObject asteroid = CelestialRegistry
            .get(CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, node.index())))
            .orElseThrow();
        StringBuilder signature = new StringBuilder();

        new OrbitalPinnedInfoContentBuilder().buildSignatureInto(signature, asteroid, 100, 100);

        for (String veinId : node.oreProfile()
            .gtOreVeinIds()) {
            assertFalse(
                signature.toString()
                    .contains(veinId));
        }
        CelestialKnowledgeClientState.clear();
    }

    @Test
    void asteroidRowsIncludeActiveSatelliteScanProgress() {
        CelestialObject frozenBelt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        AsteroidFieldProfile profile = frozenBelt.properties()
            .asteroidFieldProfile();
        AsteroidFieldNode node = AsteroidFieldResolver
            .resolveNode(CelestialObjectId.FROZEN_BELT, profile, AsteroidSlotRanges.GENERATED_SLOT_MIN);
        CelestialObject asteroid = CelestialRegistry
            .get(CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, node.index())))
            .orElseThrow();
        CelestialObjectKey asteroidKey = asteroid.id();
        CelestialDiscoveryClientState.update(
            List.of(
                new CelestialDiscoveryScanSnapshot(
                    new java.util.UUID(1L, 2L),
                    asteroidKey,
                    0.5,
                    1,
                    CelestialDiscoveryCapability.PROSPECTING,
                    CelestialDiscoveryScanSnapshot.Status.ACTIVE,
                    asteroidKey,
                    CelestialDiscoveryStep.DETECTION,
                    CelestialDiscoveryStep.DETECTION.durationTicks() / 2)));

        OrbitalPinnedInfoContentBuilder builder = new OrbitalPinnedInfoContentBuilder();
        PinnedInfoRow scanRow = scanRow(builder.buildRows(asteroid));
        StringBuilder signature = new StringBuilder();
        builder.buildSignatureInto(signature, asteroid, 100, 100);

        assertTrue(
            scanRow.value()
                .length() > 0);
        assertTrue(
            signature.toString()
                .contains("asteroidScan:DETECTION:600"));
        CelestialKnowledgeClientState.clear();
    }

    @Test
    void asteroidOreRowsFollowKnowledgeLevel() {
        CelestialObject frozenBelt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        AsteroidFieldProfile profile = frozenBelt.properties()
            .asteroidFieldProfile();
        AsteroidFieldNode node = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile)
            .stream()
            .filter(
                candidate -> !candidate.oreProfile()
                    .gtOreVeinIds()
                    .isEmpty())
            .findFirst()
            .orElseThrow();
        CelestialObject asteroid = CelestialRegistry
            .get(CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, node.index())))
            .orElseThrow();
        OrbitalPinnedInfoContentBuilder builder = new OrbitalPinnedInfoContentBuilder();

        CelestialKnowledgeClientState.apply(
            Map.of(
                asteroid.id(),
                CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.UNKNOWN)));
        PinnedInfoRow unknownRow = oreRow(builder.buildRows(asteroid));
        assertFalse(
            unknownRow.value()
                .contains(
                    node.oreProfile()
                        .id()));
        assertTrue(
            unknownRow.items()
                .isEmpty());

        CelestialKnowledgeClientState.apply(
            Map.of(
                asteroid.id(),
                CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.PROFILE)));
        PinnedInfoRow profileRow = oreRow(builder.buildRows(asteroid));
        assertNotEquals(unknownRow.value(), profileRow.value());

        CelestialKnowledgeClientState.clear();
    }

    @Test
    void clientStateExposesDiscoveryStateByKey() {
        CelestialObjectKey asteroidKey = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 3));
        CelestialKnowledgeClientState.apply(
            Map.of(
                asteroidKey,
                CelestialKnowledgeFacts.of(DiscoveryState.HIDDEN, CelestialResourceKnowledgeState.UNKNOWN)));

        assertEquals(
            DiscoveryState.HIDDEN,
            CelestialKnowledgeClientState.discoveryView()
                .discoveryState(asteroidKey)
                .orElseThrow());

        CelestialKnowledgeClientState.clear();
    }

    private static PinnedInfoRow oreRow(List<PinnedInfoRow> rows) {
        return rows.get(rows.size() - 1);
    }

    private static PinnedInfoRow scanRow(List<PinnedInfoRow> rows) {
        String scanLabel = StatCollector.translateToLocal("galaxia.gui.orbital.pinned_info.label.scan");
        return rows.stream()
            .filter(
                row -> row.label()
                    .equals(scanLabel))
            .findFirst()
            .orElseThrow();
    }
}
