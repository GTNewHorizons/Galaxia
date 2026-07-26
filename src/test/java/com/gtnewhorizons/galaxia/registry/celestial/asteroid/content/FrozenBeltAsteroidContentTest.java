package com.gtnewhorizons.galaxia.registry.celestial.asteroid.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodeKind;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class FrozenBeltAsteroidContentTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void frozenBeltProfileIncludesGeneratedFieldAndAuthoredLoreAsteroid() {
        AsteroidFieldProfile profile = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow()
            .properties()
            .asteroidFieldProfile();
        AsteroidFieldNode lore = AsteroidFieldResolver.placedNode(CelestialObjectId.FROZEN_BELT, profile, 1);
        AsteroidFieldNode generated = AsteroidFieldResolver
            .placedNode(CelestialObjectId.FROZEN_BELT, profile, AsteroidSlotRanges.GENERATED_SLOT_MIN);

        assertEquals(26, profile.totalNodes());
        assertEquals(AsteroidNodeKind.LORE, lore.kind());
        assertEquals("Karnyx", lore.displayName());
        assertEquals(AsteroidSizeClass.LARGE, lore.sizeClass());
        assertEquals(184.5, lore.angleOffsetDeg());
        assertEquals(0.73, lore.orbitalDepth01());
        assertEquals(
            "rare_crystal",
            lore.oreProfile()
                .id());
        assertEquals(DiscoveryState.DISCOVERED, lore.initialDetectionState());
        assertEquals(CelestialResourceKnowledgeState.PROFILE, AsteroidFieldResolver.initialOreKnowledge(lore));
        assertEquals(AsteroidNodeKind.GENERATED, generated.kind());
    }
}
