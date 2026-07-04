package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

final class AsteroidFieldNodeCatalogTest {

    @Test
    void catalogMergesCurrentGeneratedNodesWithSavedOnlyNodes() {
        AsteroidFieldProfile profile = profile(1);
        int savedOnlyIndex = AsteroidSlotRanges.GENERATED_SLOT_MIN + 3;
        AsteroidFieldNodeSnapshot savedOnly = snapshot(savedOnlyIndex, "Saved Ceres");

        AsteroidFieldNodeCatalog catalog = AsteroidFieldNodeCatalog
            .fromSnapshots(CelestialObjectId.FROZEN_BELT, profile, List.of(savedOnly));

        assertEquals(
            2,
            catalog.nodes()
                .size());
        assertEquals(
            "Saved Ceres",
            catalog.resolve(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, savedOnlyIndex))
                .orElseThrow()
                .displayName());
    }

    @Test
    void savedNodeSnapshotWinsOverCurrentGeneratedNodeForSameIndex() {
        AsteroidFieldProfile profile = profile(1);
        int currentIndex = AsteroidSlotRanges.GENERATED_SLOT_MIN;

        AsteroidFieldNodeCatalog catalog = AsteroidFieldNodeCatalog
            .fromSnapshots(CelestialObjectId.FROZEN_BELT, profile, List.of(snapshot(currentIndex, "Saved Identity")));

        AsteroidFieldNode resolved = catalog
            .resolve(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, currentIndex))
            .orElseThrow();
        assertEquals("Saved Identity", resolved.displayName());
        assertEquals(AsteroidNodeKind.LORE, resolved.kind());
    }

    @Test
    void duplicateSavedNodeIndexesFailLoudly() {
        AsteroidFieldProfile profile = profile(1);
        int index = AsteroidSlotRanges.GENERATED_SLOT_MIN + 1;

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> AsteroidFieldNodeCatalog.fromSnapshots(
                CelestialObjectId.FROZEN_BELT,
                profile,
                List.of(snapshot(index, "First"), snapshot(index, "Second"))));

        assertTrue(
            error.getMessage()
                .contains("duplicate asteroid node snapshot index"));
    }

    @Test
    void corruptSavedNodeSnapshotFailsLoudly() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new AsteroidFieldNodeSnapshot(
                AsteroidSlotRanges.GENERATED_SLOT_MIN,
                "Broken",
                AsteroidNodeKind.LORE,
                AsteroidSizeClass.MEDIUM,
                DiscoveryState.HIDDEN,
                AsteroidOreKnowledgeState.PROFILE,
                12.0,
                0.5,
                new AsteroidFieldNodeSnapshot.OreProfileSnapshot("metallic", List.of("galaxia:iron")),
                new AsteroidFieldNodeSnapshot.AppearanceSnapshot("generated_asteroid_tiles", 4L)));

        assertTrue(
            error.getMessage()
                .contains("hidden asteroid nodes cannot expose ore knowledge"));
    }

    private static AsteroidFieldProfile profile(int generatedNodes) {
        return AsteroidFieldProfile.builder()
            .seedSalt(99L)
            .generationVersion(1)
            .sizeCounts(generatedNodes, 0, 0)
            .radialBand(1000.0, 2000.0)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("metallic", List.of("galaxia:iron")))
            .build();
    }

    private static AsteroidFieldNodeSnapshot snapshot(int index, String displayName) {
        return new AsteroidFieldNodeSnapshot(
            index,
            displayName,
            AsteroidNodeKind.LORE,
            AsteroidSizeClass.LARGE,
            DiscoveryState.DISCOVERED,
            AsteroidOreKnowledgeState.PROFILE,
            12.0,
            0.5,
            new AsteroidFieldNodeSnapshot.OreProfileSnapshot("metallic", List.of("galaxia:iron")),
            new AsteroidFieldNodeSnapshot.AppearanceSnapshot("generated_asteroid_tiles", 4L));
    }
}
