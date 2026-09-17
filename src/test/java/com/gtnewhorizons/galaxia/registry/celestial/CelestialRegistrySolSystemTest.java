package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialRegistrySolSystemTest {

    private static final double EARTH_RADIUS_TO_AU = 23481.0;
    private static final double AU_TOLERANCE = 0.0001;

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void gtnhSolProgressionKeepsOverworldMoonMarsAndOuterSystemsInTheirAuthoredHierarchy() {
        assertBody(CelestialObjectId.SOL, CelestialObject.Class.STAR, CelestialObjectId.NOVUM_CAELUM);
        assertBody(CelestialObjectId.OVERWORLD, CelestialObject.Class.PLANET, CelestialObjectId.SOL);
        assertBody(CelestialObjectId.MOON, CelestialObject.Class.MOON, CelestialObjectId.OVERWORLD);
        assertBody(CelestialObjectId.MARS, CelestialObject.Class.PLANET, CelestialObjectId.SOL);
        assertBody(CelestialObjectId.PHOBOS, CelestialObject.Class.MOON, CelestialObjectId.MARS);
        assertBody(CelestialObjectId.DEIMOS, CelestialObject.Class.MOON, CelestialObjectId.MARS);

        assertBody(CelestialObjectId.BARNARDA, CelestialObject.Class.STAR, CelestialObjectId.NOVUM_CAELUM);
        assertBody(CelestialObjectId.TCETI_A, CelestialObject.Class.STAR, CelestialObjectId.NOVUM_CAELUM);
        assertBody(CelestialObjectId.VEGA, CelestialObject.Class.STAR, CelestialObjectId.NOVUM_CAELUM);
        assertBody(CelestialObjectId.ALPHA_CENTAURI_A, CelestialObject.Class.STAR, CelestialObjectId.NOVUM_CAELUM);

        assertEquals(
            CelestialObjectId.OVERWORLD,
            CelestialRegistry.findByDimension(DimensionEnum.OVERWORLD)
                .orElseThrow()
                .requireRegisteredId());
        assertEquals(
            CelestialObjectId.MOON,
            CelestialRegistry.findByDimension(DimensionEnum.MOON)
                .orElseThrow()
                .requireRegisteredId());
        assertEquals(
            CelestialObjectId.MARS,
            CelestialRegistry.findByDimension(DimensionEnum.MARS)
                .orElseThrow()
                .requireRegisteredId());
    }

    @Test
    void solPlanetOrbitDistancesUseRealSolarSystemSemiMajorAxes() {
        assertOrbitAu(CelestialObjectId.MERCURY, 0.387);
        assertOrbitAu(CelestialObjectId.VENUS, 0.723);
        assertOrbitAu(CelestialObjectId.OVERWORLD, 1.0);
        assertOrbitAu(CelestialObjectId.MARS, 1.524);
        assertOrbitAu(CelestialObjectId.CERES, 2.767);
        assertOrbitAu(CelestialObjectId.FROZEN_BELT, 2.8);
        assertOrbitAu(CelestialObjectId.JUPITER, 5.204);
        assertOrbitAu(CelestialObjectId.SATURN, 9.583);
        assertOrbitAu(CelestialObjectId.URANUS, 19.218);
        assertOrbitAu(CelestialObjectId.NEPTUNE, 30.11);
        assertOrbitAu(CelestialObjectId.PLUTO, 39.482);
        assertOrbitAu(CelestialObjectId.KUIPER_BELT, 43.0);
        assertOrbitAu(CelestialObjectId.HAUMEA, 43.218);
        assertOrbitAu(CelestialObjectId.MAKEMAKE, 45.715);
    }

    @Test
    void portedGtnhBodiesDefineTheirOreVeinsInGalaxia() {
        assertGalaxiaOreVeins(CelestialObjectId.OVERWORLD);
        assertGalaxiaOreVeins(CelestialObjectId.MOON);
        assertGalaxiaOreVeins(CelestialObjectId.MARS);
        assertGalaxiaOreVeins(CelestialObjectId.FROZEN_BELT);
        assertGalaxiaOreVeins(CelestialObjectId.MERCURY);
        assertGalaxiaOreVeins(CelestialObjectId.VENUS);
        assertGalaxiaOreVeins(CelestialObjectId.PHOBOS);
        assertGalaxiaOreVeins(CelestialObjectId.DEIMOS);
        assertGalaxiaOreVeins(CelestialObjectId.CERES);
        assertGalaxiaOreVeins(CelestialObjectId.IO);
        assertGalaxiaOreVeins(CelestialObjectId.EUROPA);
        assertGalaxiaOreVeins(CelestialObjectId.GANYMEDE);
        assertGalaxiaOreVeins(CelestialObjectId.CALLISTO);
        assertGalaxiaOreVeins(CelestialObjectId.ENCELADUS);
        assertGalaxiaOreVeins(CelestialObjectId.TITAN);
        assertGalaxiaOreVeins(CelestialObjectId.MIRANDA);
        assertGalaxiaOreVeins(CelestialObjectId.OBERON);
        assertGalaxiaOreVeins(CelestialObjectId.PROTEUS);
        assertGalaxiaOreVeins(CelestialObjectId.TRITON);
        assertGalaxiaOreVeins(CelestialObjectId.PLUTO);
        assertGalaxiaOreVeins(CelestialObjectId.KUIPER_BELT);
        assertGalaxiaOreVeins(CelestialObjectId.HAUMEA);
        assertGalaxiaOreVeins(CelestialObjectId.MAKEMAKE);
        assertGalaxiaOreVeins(CelestialObjectId.BARNARD_C);
        assertGalaxiaOreVeins(CelestialObjectId.BARNARD_E);
        assertGalaxiaOreVeins(CelestialObjectId.BARNARD_F);
        assertGalaxiaOreVeins(CelestialObjectId.CENTAURI_BB);
        assertGalaxiaOreVeins(CelestialObjectId.TCETI_E);
        assertGalaxiaOreVeins(CelestialObjectId.VEGA_B);
    }

    /** Integration contract: the map includes the celestial catalog authored by the GTNH space mods. */
    @Test
    void gtnhCatalogKeepsDisplayOnlyBodiesAndTheirParentSystems() {
        assertChildren(
            CelestialObjectId.SOL,
            "MERCURY VENUS OVERWORLD MARS CERES FROZEN_BELT JUPITER SATURN URANUS NEPTUNE PLUTO KUIPER_BELT HAUMEA MAKEMAKE ERIS");
        assertChildren(
            CelestialObjectId.BARNARDA,
            "BARNARD_B BARNARD_C BARNARD_D BARNARD_E BARNARD_F BARNARD_G BARNARD_BELT");
        assertChildren(CelestialObjectId.TCETI_A, "TCETI_B TCETI_C TCETI_D TCETI_E TCETI_F TCETI_G TCETI_BELT");
        assertChildren(CelestialObjectId.VEGA, "VEGA_B VEGA_C VEGA_INNER_BELT VEGA_OUTER_BELT");
        assertChildren(CelestialObjectId.ALPHA_CENTAURI_A, "ALPHA_CENTAURI_B CENTAURI_BB");
        assertChildren(CelestialObjectId.ROSS_128, "ROSS_128_B");
        assertChildren(CelestialObjectId.ROSS_128_B, "ROSS_128_BA");
        assertChildren(CelestialObjectId.RA, "AMUN OSIRIS HORUS BAAL MEHEN_BELT SEKHMET ANUBIS");
        assertChildren(CelestialObjectId.BAAL, "BAAL_RINGS KHONSU NEPER IAH");
        assertChildren(CelestialObjectId.SEKHMET, "BAST MAAHES THOTH SETH");
        assertChildren(CelestialObjectId.ANUBIS, "KEBE");
        assertChildren(CelestialObjectId.MARS, "PHOBOS DEIMOS");
        assertChildren(CelestialObjectId.JUPITER, "IO EUROPA GANYMEDE CALLISTO");
        assertChildren(CelestialObjectId.SATURN, "MIMAS ENCELADUS TETHYS DIONE RHEA TITAN IAPETUS");
        assertChildren(CelestialObjectId.URANUS, "MIRANDA ARIEL UMBRIEL TITANIA OBERON");
        assertChildren(CelestialObjectId.PLUTO, "CHARON");
        assertChildren(CelestialObjectId.NEPTUNE, "PROTEUS TRITON");
    }

    /** Bug regression: a galaxy overview must not stack two stars at the same authored position. */
    @Test
    void galaxyStarsHaveDistinctPositions() {
        var positions = new HashSet<>();
        for (var star : CelestialRegistry.getChildren(CelestialObjectKey.registered(CelestialObjectId.NOVUM_CAELUM))) {
            assertNotNull(star.absolutePosition());
            assertTrue(positions.add(star.absolutePosition()), () -> "Overlapping star: " + star.key());
        }
    }

    /** Product contract: orbital stations belong to Earth, not a second celestial destination. */
    @Test
    void overworldOwnsOrbitalStationCapabilityWithoutASeparateOrbitBody() {
        var earth = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        assertTrue(
            earth.properties()
                .canCreateStation());
        assertChildren(CelestialObjectId.OVERWORLD, "MOON");
        assertNull(DimensionEnum.fromId(-19));
    }

    /** Product contract: satellite distances use JPL mean elements in km, not Galacticraft display distances in AU. */
    @Test
    void knownMoonOrbitRadiiMatchMeanSatelliteElements() {
        assertMoonOrbitKm(CelestialObjectId.MOON, 384400.0);
        assertMoonOrbitKm(CelestialObjectId.PHOBOS, 9375.0);
        assertMoonOrbitKm(CelestialObjectId.DEIMOS, 23457.0);
        assertMoonOrbitKm(CelestialObjectId.IO, 421800.0);
        assertMoonOrbitKm(CelestialObjectId.EUROPA, 671100.0);
        assertMoonOrbitKm(CelestialObjectId.GANYMEDE, 1070400.0);
        assertMoonOrbitKm(CelestialObjectId.CALLISTO, 1882700.0);
        assertMoonOrbitKm(CelestialObjectId.MIMAS, 186000.0);
        assertMoonOrbitKm(CelestialObjectId.ENCELADUS, 238400.0);
        assertMoonOrbitKm(CelestialObjectId.TETHYS, 295000.0);
        assertMoonOrbitKm(CelestialObjectId.DIONE, 377700.0);
        assertMoonOrbitKm(CelestialObjectId.RHEA, 527200.0);
        assertMoonOrbitKm(CelestialObjectId.TITAN, 1221900.0);
        assertMoonOrbitKm(CelestialObjectId.IAPETUS, 3561700.0);
        assertMoonOrbitKm(CelestialObjectId.MIRANDA, 129846.0);
        assertMoonOrbitKm(CelestialObjectId.ARIEL, 190929.0);
        assertMoonOrbitKm(CelestialObjectId.UMBRIEL, 265986.0);
        assertMoonOrbitKm(CelestialObjectId.TITANIA, 436298.0);
        assertMoonOrbitKm(CelestialObjectId.OBERON, 583511.0);
        assertMoonOrbitKm(CelestialObjectId.PROTEUS, 117600.0);
        assertMoonOrbitKm(CelestialObjectId.TRITON, 354800.0);
        assertMoonOrbitKm(CelestialObjectId.CHARON, 19600.0);
    }

    /** Product contract: scaled gravity preserves the observed order of lunar and planetary orbital periods. */
    @Test
    void moonPeriodsStayProportionalToPlanetYears() {
        double lunarYearFraction = period(CelestialObjectId.OVERWORLD, CelestialObjectId.MOON)
            / period(CelestialObjectId.SOL, CelestialObjectId.OVERWORLD);
        assertTrue(lunarYearFraction > 0.07 && lunarYearFraction < 0.08);
        double phobosYearFraction = period(CelestialObjectId.MARS, CelestialObjectId.PHOBOS)
            / period(CelestialObjectId.SOL, CelestialObjectId.MARS);
        assertTrue(phobosYearFraction > 0.0004 && phobosYearFraction < 0.0006);
    }

    private static double period(CelestialObjectId parent, CelestialObjectId child) {
        return OrbitalMechanics.getOrbitalPeriod(
            CelestialRegistry.get(parent)
                .orElseThrow(),
            CelestialRegistry.get(child)
                .orElseThrow()
                .orbitalParams());
    }

    private static void assertMoonOrbitKm(CelestialObjectId id, double expectedKm) {
        assertEquals(
            expectedKm,
            CelestialRegistry.get(id)
                .orElseThrow()
                .orbitalParams()
                .semiMajorAxis() * 6371.0,
            1.0,
            () -> "Moon orbit: " + id);
    }

    /** Bug regression: moons stay near their planet rather than crossing neighboring planetary orbits. */
    @Test
    void solMoonOrbitsStayInsideTheGapToNeighboringPlanets() {
        var planets = CelestialRegistry.getChildren(CelestialObjectKey.registered(CelestialObjectId.SOL));
        for (var planet : planets) {
            double nearestGap = Double.POSITIVE_INFINITY;
            for (var neighbor : planets) {
                if (neighbor == planet || neighbor.objectClass() == CelestialObject.Class.ASTEROID_BELT) continue;
                nearestGap = Math.min(
                    nearestGap,
                    Math.abs(
                        planet.orbitalParams()
                            .semiMajorAxis()
                            - neighbor.orbitalParams()
                                .semiMajorAxis()));
            }
            for (var moon : CelestialRegistry.getChildren(planet.key())) {
                if (moon.objectClass() != CelestialObject.Class.MOON) continue;
                double farthest = moon.orbitalParams()
                    .apogee();
                assertTrue(farthest < nearestGap, () -> "Moon reaches another planet's orbit: " + moon.key());
            }
        }
    }

    private static void assertChildren(CelestialObjectId parent, String expectedNames) {
        var children = CelestialRegistry.getChildren(CelestialObjectKey.registered(parent))
            .stream()
            .map(CelestialObject::requireRegisteredId)
            .collect(Collectors.toSet());
        var expected = Arrays.stream(expectedNames.split(" "))
            .map(CelestialObjectId::valueOf)
            .collect(Collectors.toSet());
        assertEquals(expected, children, () -> "GTNH catalog for " + parent);
    }

    private static void assertBody(CelestialObjectId id, CelestialObject.Class objectClass,
        CelestialObjectId parentId) {
        CelestialObject body = CelestialRegistry.get(id)
            .orElseThrow();

        assertEquals(objectClass, body.objectClass());
        assertEquals(CelestialObjectKey.registered(parentId), body.parentKey());
    }

    private static void assertOrbitAu(CelestialObjectId id, double expectedAu) {
        CelestialObject body = CelestialRegistry.get(id)
            .orElseThrow();

        assertEquals(
            expectedAu,
            body.orbitalParams()
                .semiMajorAxis() / EARTH_RADIUS_TO_AU,
            AU_TOLERANCE);
    }

    private static void assertGalaxiaOreVeins(CelestialObjectId id) {
        CelestialObject body = CelestialRegistry.get(id)
            .orElseThrow();

        assertFalse(
            "undefined".equals(
                body.properties()
                    .oreProfile()),
            () -> id + " still uses the placeholder ore profile");
        assertFalse(
            body.properties()
                .gtOreDepositIds()
                .isEmpty(),
            () -> id + " does not define Galaxia GT ore vein ids");
    }

    /** Bug regression: small ores and ice deposits are included in the planetary resource catalog. */
    @Test
    void planetaryResourcesIncludeSmallOresAndAsteroidIceDeposits() {
        assertTrue(
            CelestialRegistry.get(CelestialObjectId.MOON)
                .orElseThrow()
                .properties()
                .gtOreDepositIds()
                .contains("ore.small.meteoriciron"));
        for (var id : java.util.List.of(CelestialObjectId.FROZEN_BELT, CelestialObjectId.KUIPER_BELT)) {
            assertTrue(
                CelestialRegistry.get(id)
                    .orElseThrow()
                    .properties()
                    .gtOreDepositIds()
                    .containsAll(
                        java.util.List.of(
                            "ore.mix.nitrogenice",
                            "ore.mix.hydrocarbonice",
                            "ore.mix.carbonice",
                            "ore.mix.hhoice",
                            "ore.mix.sulfurice")));
        }
    }

    /** Bug regression: imported Ross and Ra destinations carry their GTNH ore pools. */
    @Test
    void rossAndRaResourceDestinationsHaveAuthoredOrePools() {
        for (var id : java.util.List.of(
            CelestialObjectId.ROSS_128_B,
            CelestialObjectId.ROSS_128_BA,
            CelestialObjectId.HORUS,
            CelestialObjectId.ANUBIS,
            CelestialObjectId.NEPER,
            CelestialObjectId.MAAHES,
            CelestialObjectId.SETH,
            CelestialObjectId.MEHEN_BELT)) {
            assertGalaxiaOreVeins(id);
        }
        assertTrue(
            CelestialRegistry.get(CelestialObjectId.ROSS_128_B)
                .orElseThrow()
                .properties()
                .gtOreDepositIds()
                .contains("ore.mix.ross128.Thorianit"));
        assertTrue(
            CelestialRegistry.get(CelestialObjectId.HORUS)
                .orElseThrow()
                .properties()
                .gtOreDepositIds()
                .contains("ore.mix.cosmicneutronium"));
    }
}
