package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class AsteroidDomainTypesTest {

    @Test
    void domainVocabularyMatchesAsteroidFieldRules() {
        assertArrayEquals(
            new AsteroidNodeKind[] { AsteroidNodeKind.UNIQUE, AsteroidNodeKind.NAMED, AsteroidNodeKind.GENERATED },
            AsteroidNodeKind.values());
        assertArrayEquals(
            new AsteroidSizeClass[] { AsteroidSizeClass.LARGE, AsteroidSizeClass.MEDIUM, AsteroidSizeClass.SMALL },
            AsteroidSizeClass.values());
        assertArrayEquals(
            new AsteroidDetectionState[] { AsteroidDetectionState.HIDDEN, AsteroidDetectionState.DETECTED },
            AsteroidDetectionState.values());
        assertArrayEquals(
            new AsteroidOreKnowledgeState[] { AsteroidOreKnowledgeState.UNKNOWN, AsteroidOreKnowledgeState.SIGNATURE,
                AsteroidOreKnowledgeState.PROFILE },
            AsteroidOreKnowledgeState.values());
    }

    @Test
    void appearanceProfileKeepsStableIconRecipeSeed() {
        AsteroidAppearanceProfile appearance = new AsteroidAppearanceProfile("rocky_tiles", 1234L);

        assertEquals("rocky_tiles", appearance.iconRecipeId());
        assertEquals(1234L, appearance.variantSeed());
        assertThrows(IllegalArgumentException.class, () -> new AsteroidAppearanceProfile("", 1L));
        assertThrows(IllegalArgumentException.class, () -> new AsteroidAppearanceProfile(" ", 1L));
    }

    @Test
    void oreProfileRequiresStablePositiveWeights() {
        assertThrows(IllegalArgumentException.class, () -> new AsteroidOreProfile("", 1.0, List.of("vein")));
        assertThrows(IllegalArgumentException.class, () -> new AsteroidOreProfile("metallic", 0.0, List.of("vein")));
        assertThrows(IllegalArgumentException.class, () -> new AsteroidOreProfile("metallic", -1.0, List.of("vein")));

        List<String> veins = new ArrayList<>();
        veins.add("galaxia:iron");
        AsteroidOreProfile profile = new AsteroidOreProfile("metallic", 2.5, veins);
        veins.add("galaxia:copper");

        assertEquals("metallic", profile.id());
        assertEquals(2.5, profile.weight());
        assertEquals(List.of("galaxia:iron"), profile.gtOreVeinIds());
    }

    @Test
    void minorBodyIdKeepsParentAndIndexStructured() {
        MinorCelestialBodyId id = new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 42);

        assertEquals(CelestialObjectId.FROZEN_BELT, id.parentBeltId());
        assertEquals(42, id.index());
        assertThrows(NullPointerException.class, () -> new MinorCelestialBodyId(null, 0));
        assertThrows(IllegalArgumentException.class, () -> new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, -1));
    }
}
