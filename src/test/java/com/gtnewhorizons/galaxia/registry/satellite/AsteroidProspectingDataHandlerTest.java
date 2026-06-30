package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidDetectionState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledge;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodeKind;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodePreset;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;

final class AsteroidProspectingDataHandlerTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000121");
    private static final CelestialObjectId BELT = CelestialObjectId.FROZEN_BELT;

    @Test
    void prospectingCompletionDetectsHiddenAsteroidsBeforeRevealingOreProfiles() {
        AsteroidFieldKnowledgeStore store = new AsteroidFieldKnowledgeStore();
        AsteroidFieldProfile profile = profile();
        AsteroidProspectingDataHandler handler = new AsteroidProspectingDataHandler(
            store,
            bodyId -> bodyId == BELT ? Optional.of(profile) : Optional.empty());
        SatelliteDataJobService.ProductionEvent event = prospectingEvent(BELT);

        Optional<AsteroidFieldNode> detected = handler.handle(event);

        assertTrue(detected.isPresent());
        AsteroidFieldKnowledge knowledge = store.get(TEAM, BELT)
            .orElseThrow();
        assertEquals(
            AsteroidDetectionState.DETECTED,
            knowledge.entryFor(
                detected.get()
                    .id())
                .detectionState());
        assertEquals(
            AsteroidOreKnowledgeState.UNKNOWN,
            knowledge.entryFor(
                detected.get()
                    .id())
                .oreKnowledgeState());

        Optional<AsteroidFieldNode> prospected = handler.handle(event);

        assertEquals(detected, prospected);
        assertEquals(
            AsteroidOreKnowledgeState.SIGNATURE,
            knowledge.entryFor(
                detected.get()
                    .id())
                .oreKnowledgeState());

        Optional<AsteroidFieldNode> profiled = handler.handle(event);

        assertEquals(detected, profiled);
        assertEquals(
            AsteroidOreKnowledgeState.PROFILE,
            knowledge.entryFor(
                detected.get()
                    .id())
                .oreKnowledgeState());
    }

    @Test
    void nonAsteroidProspectingCompletionDoesNotCreateKnowledgeState() {
        AsteroidFieldKnowledgeStore store = new AsteroidFieldKnowledgeStore();
        AsteroidProspectingDataHandler handler = new AsteroidProspectingDataHandler(store, bodyId -> Optional.empty());

        Optional<AsteroidFieldNode> result = handler.handle(prospectingEvent(CelestialObjectId.MARS));

        assertTrue(result.isEmpty());
        assertTrue(
            store.get(TEAM, CelestialObjectId.MARS)
                .isEmpty());
    }

    private static SatelliteDataJobService.ProductionEvent prospectingEvent(CelestialObjectId bodyId) {
        return new SatelliteDataJobService.ProductionEvent(
            TEAM,
            bodyId,
            SatelliteDataKey.origin(SatelliteDataType.PROSPECTING, bodyId),
            SatelliteBandwidthFormatter.kilobits(10L));
    }

    private static AsteroidFieldProfile profile() {
        return AsteroidFieldProfile.builder()
            .seedSalt(0x51A7E11DL)
            .generationVersion(1)
            .sizeCounts(0, 0, 1)
            .radialBand(10.0, 12.0)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("metallic", 1.0, List.of("ore.mix.iron")))
            .nodePreset(
                new AsteroidNodePreset(
                    1,
                    AsteroidNodeKind.LORE,
                    "scan_anchor",
                    "Scan Anchor",
                    AsteroidSizeClass.LARGE,
                    AsteroidDetectionState.DETECTED,
                    AsteroidOreKnowledgeState.PROFILE,
                    0.0,
                    0.5,
                    null,
                    null))
            .build();
    }
}
