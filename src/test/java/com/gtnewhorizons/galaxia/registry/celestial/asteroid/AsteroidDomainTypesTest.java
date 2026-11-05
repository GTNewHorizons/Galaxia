package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class AsteroidDomainTypesTest {

    @Test
    void appearanceProfileKeepsStableIconRecipeSeed() {
        AsteroidAppearanceProfile appearance = new AsteroidAppearanceProfile("rocky_tiles", 1234L);

        assertEquals("rocky_tiles", appearance.iconRecipeId());
        assertEquals(1234L, appearance.variantSeed());
        assertThrows(IllegalArgumentException.class, () -> new AsteroidAppearanceProfile("", 1L));
        assertThrows(IllegalArgumentException.class, () -> new AsteroidAppearanceProfile(" ", 1L));
    }

    @Test
    void oreProfileKeepsStableCompositionWhileFieldProfileOwnsWeights() {
        assertThrows(IllegalArgumentException.class, () -> new AsteroidOreProfile("", List.of("vein")));

        List<String> veins = new ArrayList<>();
        veins.add("galaxia:iron");
        AsteroidOreProfile profile = new AsteroidOreProfile("metallic", veins);
        veins.add("galaxia:copper");

        assertEquals("metallic", profile.id());
        assertEquals(List.of("galaxia:iron"), profile.gtOreVeinIds());
        AsteroidFieldProfile fieldProfile = AsteroidFieldProfile.builder()
            .sizeCounts(1, 0, 0)
            .radialBand(1.0, 2.0)
            .placementConnectionRadius(1000.0)
            .oreProfile(profile, 2.5)
            .build();
        assertEquals(profile, fieldProfile.selectOreProfile(0.0));
        assertThrows(
            IllegalArgumentException.class,
            () -> AsteroidFieldProfile.builder()
                .sizeCounts(1, 0, 0)
                .radialBand(1.0, 2.0)
                .placementConnectionRadius(1000.0)
                .oreProfile(profile, -1.0));
    }

    @Test
    void minorBodyIdKeepsParentAndIndexStructured() {
        MinorCelestialBodyId id = new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 42);

        assertEquals(CelestialObjectId.FROZEN_BELT, id.parentBodyId());
        assertEquals(42, id.index());
        assertThrows(IllegalArgumentException.class, () -> new MinorCelestialBodyId(null, 0));
        assertThrows(IllegalArgumentException.class, () -> new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, -1));
    }
}
