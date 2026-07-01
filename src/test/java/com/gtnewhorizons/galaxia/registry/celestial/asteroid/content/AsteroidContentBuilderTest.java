package com.gtnewhorizons.galaxia.registry.celestial.asteroid.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidDetectionState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodeKind;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;

final class AsteroidContentBuilderTest {

    @Test
    void authoredLoreAndUniqueAsteroidsMergeIntoBeltProfileWithoutUsingGeneratedSlots() {
        AsteroidOreProfile rareCrystal = new AsteroidOreProfile("rare_crystal", 1.0, List.of("ore.mix.redstone"));
        Map<CelestialObjectId, AsteroidFieldProfile> profiles = new AsteroidContentBuilder()
            .field(
                CelestialObjectId.FROZEN_BELT,
                field -> field.seedSalt(11L)
                    .generationVersion(1)
                    .sizeCounts(1, 1, 1)
                    .radialBand(10.0, 20.0)
                    .satelliteScanRadius(5.0)
                    .oreProfile(rareCrystal))
            .lore(
                "karnyx",
                lore -> lore.belt(CelestialObjectId.FROZEN_BELT)
                    .slot(1)
                    .name("Karnyx")
                    .size(AsteroidSizeClass.LARGE)
                    .position(184.5, 0.73)
                    .oreProfile("rare_crystal")
                    .detected()
                    .oreProfileKnown())
            .unique(
                "icelock",
                unique -> unique.belt(CelestialObjectId.FROZEN_BELT)
                    .slot(AsteroidSlotRanges.UNIQUE_SLOT_MIN)
                    .name("Icelock")
                    .size(AsteroidSizeClass.MEDIUM)
                    .position(184.5, 0.73)
                    .hidden()
                    .useBeltOrePool())
            .buildProfiles();

        AsteroidFieldProfile profile = profiles.get(CelestialObjectId.FROZEN_BELT);
        AsteroidFieldNode lore = AsteroidFieldResolver.resolveNode(CelestialObjectId.FROZEN_BELT, profile, 1);
        AsteroidFieldNode unique = AsteroidFieldResolver
            .resolveNode(CelestialObjectId.FROZEN_BELT, profile, AsteroidSlotRanges.UNIQUE_SLOT_MIN);
        AsteroidFieldNode generated = AsteroidFieldResolver
            .resolveNode(CelestialObjectId.FROZEN_BELT, profile, AsteroidSlotRanges.GENERATED_SLOT_MIN);

        assertEquals(AsteroidNodeKind.LORE, lore.kind());
        assertEquals("Karnyx", lore.displayName());
        assertEquals(AsteroidSizeClass.LARGE, lore.sizeClass());
        assertEquals(184.5, lore.angleOffsetDeg());
        assertEquals(0.73, lore.orbitalDepth01());
        assertEquals(
            "rare_crystal",
            lore.oreProfile()
                .id());
        assertEquals(AsteroidDetectionState.DETECTED, AsteroidFieldResolver.initialDetectionState(lore));
        assertEquals(AsteroidOreKnowledgeState.PROFILE, AsteroidFieldResolver.initialOreKnowledge(lore));

        assertEquals(AsteroidNodeKind.UNIQUE, unique.kind());
        assertEquals("Icelock", unique.displayName());
        assertEquals(AsteroidDetectionState.HIDDEN, AsteroidFieldResolver.initialDetectionState(unique));
        assertEquals(AsteroidNodeKind.GENERATED, generated.kind());
    }
}
