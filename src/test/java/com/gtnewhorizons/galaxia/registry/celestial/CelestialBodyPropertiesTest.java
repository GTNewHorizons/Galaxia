package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreProfile;

final class CelestialBodyPropertiesTest {

    @Test
    void atmosphereCompositionUsesWeightedFluidIngredients() {
        Fluid sulfuricAcid = new Fluid("sulfuric_acid");
        Fluid nitrogen = new Fluid("nitrogen");

        CelestialBodyProperties properties = CelestialBodyProperties.builder()
            .surfacePressurePa(1.0)
            .addAtmosphereIngredient(sulfuricAcid, 80.0)
            .addAtmosphereIngredient(nitrogen, 20.0)
            .build();

        assertEquals(1.0, properties.surfacePressurePa());
        assertEquals(
            List.of(
                new CelestialBodyProperties.AtmosphereIngredient(sulfuricAcid, 80.0),
                new CelestialBodyProperties.AtmosphereIngredient(nitrogen, 20.0)),
            properties.atmosphereIngredients());
        assertEquals(12.0, properties.atmosphereWeightedAverage(fluid -> fluid == sulfuricAcid ? 10.0 : 20.0));
    }

    @Test
    void zeroSurfacePressureRepresentsNoAtmosphere() {
        CelestialBodyProperties properties = CelestialBodyProperties.builder()
            .surfacePressurePa(0.0)
            .build();

        assertEquals(0.0, properties.surfacePressurePa());
        assertEquals(0.0, properties.atmosphereWeightedAverage(fluid -> 10.0));
    }

    @Test
    void atmosphereCompositionCanReuseAnotherBodyWithoutCopyingPressure() {
        Fluid carbonDioxide = new Fluid("carbon_dioxide");
        Fluid nitrogen = new Fluid("nitrogen");

        CelestialBodyProperties source = CelestialBodyProperties.builder()
            .surfacePressurePa(610.0)
            .addAtmosphereIngredient(carbonDioxide, 95.0)
            .addAtmosphereIngredient(nitrogen, 5.0)
            .build();
        CelestialBodyProperties derived = CelestialBodyProperties.builder()
            .surfacePressurePa(120.0)
            .copyAtmosphereCompositionFrom(source)
            .build();

        assertEquals(120.0, derived.surfacePressurePa());
        assertEquals(source.atmosphereIngredients(), derived.atmosphereIngredients());
        assertEquals(19.0, derived.atmosphereWeightedAverage(fluid -> fluid == carbonDioxide ? 20.0 : 0.0));
    }

    @Test
    void atmosphereCompositionSourceSurvivesBuilderRoundTrip() {
        Fluid carbonDioxide = new Fluid("carbon_dioxide");

        CelestialBodyProperties source = CelestialBodyProperties.builder()
            .surfacePressurePa(610.0)
            .addAtmosphereIngredient(carbonDioxide, 1.0)
            .build();
        CelestialBodyProperties derived = CelestialBodyProperties.builder()
            .surfacePressurePa(120.0)
            .copyAtmosphereCompositionFrom(source)
            .build();

        CelestialBodyProperties rebuilt = derived.toBuilder()
            .build();

        assertEquals(120.0, rebuilt.surfacePressurePa());
        assertEquals(source.atmosphereIngredients(), rebuilt.atmosphereIngredients());
    }

    @Test
    void starmapAtmosphericDragDefaultsToNeutralMultiplier() {
        assertEquals(
            1.0,
            CelestialBodyProperties.builder()
                .build()
                .starmapAtmosphericDrag());
        assertEquals(
            0.25,
            CelestialBodyProperties.builder()
                .starmapAtmosphericDrag(0.25)
                .build()
                .starmapAtmosphericDrag());
    }

    @Test
    void builderPopulatesTypedPropertyGroupsAndCompatibilityAccessors() {
        CelestialBodyProperties properties = CelestialBodyProperties.builder()
            .visitable(true)
            .canCreateOutpost(true)
            .localGravityG(0.25)
            .radiusEarthRelative(0.5)
            .orbitalGravity(10.0, 20.0)
            .build();

        assertEquals(
            true,
            properties.capabilities()
                .visitable());
        assertEquals(true, properties.canCreateOutpost());
        assertEquals(
            0.25,
            properties.physical()
                .localGravityG());
        assertEquals(0.25, properties.localGravityG());
        assertEquals(
            20.0,
            properties.orbital()
                .sphereOfInfluenceRadius());
    }

    @Test
    void asteroidFieldProfileSurvivesBuilderRoundTrip() {
        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .sizeCounts(1, 1, 1)
            .radialBand(10.0, 20.0)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("metallic", List.of("galaxia:iron")))
            .build();

        CelestialBodyProperties properties = CelestialBodyProperties.builder()
            .asteroidFieldProfile(profile)
            .build();

        assertEquals(profile, properties.asteroidFieldProfile());
        assertEquals(
            profile,
            properties.toBuilder()
                .build()
                .asteroidFieldProfile());
    }

    @Test
    void atmosphereAuthoringRejectsInvalidInputs() {
        Fluid nitrogen = new Fluid("nitrogen");

        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .surfacePressurePa(-1.0));
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .surfacePressurePa(Double.NaN));
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .surfacePressurePa(Double.POSITIVE_INFINITY));

        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .addAtmosphereIngredient(null, 1.0));
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .addAtmosphereIngredient(nitrogen, 0.0));
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .addAtmosphereIngredient(nitrogen, Double.NaN));
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .addAtmosphereIngredient(nitrogen, Double.POSITIVE_INFINITY));
        assertThrows(
            IllegalStateException.class,
            () -> CelestialBodyProperties.builder()
                .surfacePressurePa(1.0)
                .build());
        assertThrows(
            IllegalStateException.class,
            () -> CelestialBodyProperties.builder()
                .addAtmosphereIngredient(nitrogen, 1.0)
                .build());
        assertThrows(
            NullPointerException.class,
            () -> CelestialBodyProperties.builder()
                .surfacePressurePa(1.0)
                .addAtmosphereIngredient(nitrogen, 1.0)
                .build()
                .atmosphereWeightedAverage(null));
    }

    @Test
    void directRecordConstructionRejectsInvalidAtmosphereIngredients() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new CelestialBodyProperties(
                false,
                false,
                false,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                "",
                List.of(),
                0.0,
                0.0,
                1.0,
                1.0,
                Collections.singletonList(null),
                null,
                null,
                null,
                null,
                Map.of()));
        assertThrows(
            IllegalStateException.class,
            () -> new CelestialBodyProperties(
                false,
                false,
                false,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                "",
                List.of(),
                0.0,
                0.0,
                0.0,
                1.0,
                List.of(new CelestialBodyProperties.AtmosphereIngredient(new Fluid("nitrogen"), 1.0)),
                null,
                null,
                null,
                null,
                Map.of()));
    }

    @Test
    void physicalEnvironmentAuthoringRejectsInvalidInputs() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .radiation(-1.0));
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .radiation(Double.NaN));
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .radiation(Double.POSITIVE_INFINITY));

        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .temperature(-1.0));
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .temperature(Double.NaN));
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .temperature(Double.POSITIVE_INFINITY));

        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .starmapAtmosphericDrag(0.0));
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .starmapAtmosphericDrag(-1.0));
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .starmapAtmosphericDrag(Double.NaN));
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialBodyProperties.builder()
                .starmapAtmosphericDrag(Double.POSITIVE_INFINITY));
    }
}
