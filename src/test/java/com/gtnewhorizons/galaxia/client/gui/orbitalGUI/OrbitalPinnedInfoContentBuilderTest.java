package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.util.StatCollector;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanPass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidSatelliteScanSnapshot;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidScanClientState;
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
        AsteroidFieldClientKnowledgeState.updateFields(
            List.of(
                new AsteroidFieldKnowledgeSnapshot(
                    CelestialObjectId.FROZEN_BELT,
                    List.of(
                        new AsteroidFieldKnowledgeSnapshot.Entry(
                            node.index(),
                            DiscoveryState.DISCOVERED,
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
        AsteroidScanClientState.updateScans(
            List.of(
                new AsteroidSatelliteScanSnapshot(
                    CelestialObjectId.FROZEN_BELT,
                    node.id(),
                    node.id(),
                    AsteroidFieldScanPass.DETECTION,
                    AsteroidFieldScanPass.DETECTION.durationTicks() / 2,
                    1)),
            List.of());

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

        AsteroidFieldClientKnowledgeState.updateFields(snapshot(node, AsteroidOreKnowledgeState.UNKNOWN));
        PinnedInfoRow unknownRow = oreRow(builder.buildRows(asteroid));
        assertFalse(
            unknownRow.value()
                .contains(
                    node.oreProfile()
                        .id()));
        assertTrue(
            unknownRow.items()
                .isEmpty());

        AsteroidFieldClientKnowledgeState.updateFields(snapshot(node, AsteroidOreKnowledgeState.SIGNATURE));
        PinnedInfoRow signatureRow = oreRow(builder.buildRows(asteroid));
        assertNotEquals(unknownRow.value(), signatureRow.value());
        assertTrue(
            signatureRow.items()
                .isEmpty());

        AsteroidFieldClientKnowledgeState.updateFields(snapshot(node, AsteroidOreKnowledgeState.PROFILE));
        PinnedInfoRow profileRow = oreRow(builder.buildRows(asteroid));
        assertNotEquals(unknownRow.value(), profileRow.value());
        assertNotEquals(signatureRow.value(), profileRow.value());

        CelestialKnowledgeClientState.clear();
    }

    @Test
    void clientStateExposesDiscoveryStateByKey() {
        AsteroidFieldClientKnowledgeState.updateFields(
            List.of(
                new AsteroidFieldKnowledgeSnapshot(
                    CelestialObjectId.FROZEN_BELT,
                    List.of(
                        new AsteroidFieldKnowledgeSnapshot.Entry(
                            8,
                            DiscoveryState.HIDDEN,
                            AsteroidOreKnowledgeState.UNKNOWN)))));
        CelestialObjectKey asteroidKey = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 8));

        assertEquals(
            DiscoveryState.HIDDEN,
            CelestialKnowledgeClientState.discoveryState(asteroidKey)
                .orElseThrow());

        CelestialKnowledgeClientState.clear();
    }

    private static List<AsteroidFieldKnowledgeSnapshot> snapshot(AsteroidFieldNode node,
        AsteroidOreKnowledgeState oreKnowledgeState) {
        return List.of(
            new AsteroidFieldKnowledgeSnapshot(
                CelestialObjectId.FROZEN_BELT,
                List.of(
                    new AsteroidFieldKnowledgeSnapshot.Entry(
                        node.index(),
                        DiscoveryState.DISCOVERED,
                        oreKnowledgeState))));
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
