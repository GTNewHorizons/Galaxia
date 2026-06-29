package com.gtnewhorizons.galaxia.registry.celestial.asteroid.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodeKind;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;

final class AsteroidContentRegistryTest {

    @Test
    void frozenBeltProfileComposesGeneratedAndLoreAsteroids() {
        AsteroidFieldProfile profile = AsteroidContentRegistry.profile(CelestialObjectId.FROZEN_BELT);

        AsteroidFieldNode lore = AsteroidFieldResolver.resolveNode(CelestialObjectId.FROZEN_BELT, profile, 1);
        AsteroidFieldNode generated = AsteroidFieldResolver
            .resolveNode(CelestialObjectId.FROZEN_BELT, profile, AsteroidSlotRanges.GENERATED_SLOT_MIN);

        assertEquals(AsteroidNodeKind.LORE, lore.kind());
        assertEquals("Karnyx", lore.displayName());
        assertEquals(AsteroidNodeKind.GENERATED, generated.kind());
    }
}
