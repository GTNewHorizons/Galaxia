package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidDetectionState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
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
        AsteroidFieldClientState.update(
            List.of(
                new AsteroidFieldKnowledgeSnapshot(
                    CelestialObjectId.FROZEN_BELT,
                    List.of(
                        new AsteroidFieldKnowledgeSnapshot.Entry(
                            node.index(),
                            AsteroidDetectionState.DETECTED,
                            AsteroidOreKnowledgeState.UNKNOWN)))));
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
        AsteroidFieldClientState.clear();
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

        AsteroidFieldClientState.update(snapshot(node, AsteroidOreKnowledgeState.UNKNOWN));
        PinnedInfoRow unknownRow = oreRow(builder.buildRows(asteroid));
        assertFalse(
            unknownRow.value()
                .contains(
                    node.oreProfile()
                        .id()));
        assertTrue(
            unknownRow.items()
                .isEmpty());

        AsteroidFieldClientState.update(snapshot(node, AsteroidOreKnowledgeState.SIGNATURE));
        PinnedInfoRow signatureRow = oreRow(builder.buildRows(asteroid));
        assertNotEquals(unknownRow.value(), signatureRow.value());
        assertTrue(
            signatureRow.items()
                .isEmpty());

        AsteroidFieldClientState.update(snapshot(node, AsteroidOreKnowledgeState.PROFILE));
        PinnedInfoRow profileRow = oreRow(builder.buildRows(asteroid));
        assertNotEquals(unknownRow.value(), profileRow.value());
        assertNotEquals(signatureRow.value(), profileRow.value());

        AsteroidFieldClientState.clear();
    }

    private static List<AsteroidFieldKnowledgeSnapshot> snapshot(AsteroidFieldNode node,
        AsteroidOreKnowledgeState oreKnowledgeState) {
        return List.of(
            new AsteroidFieldKnowledgeSnapshot(
                CelestialObjectId.FROZEN_BELT,
                List.of(
                    new AsteroidFieldKnowledgeSnapshot.Entry(
                        node.index(),
                        AsteroidDetectionState.DETECTED,
                        oreKnowledgeState))));
    }

    private static PinnedInfoRow oreRow(List<PinnedInfoRow> rows) {
        return rows.get(rows.size() - 1);
    }
}
