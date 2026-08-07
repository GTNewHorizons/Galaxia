package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalParams;

final class OrbitalBodyZoomTest {

    private static final CelestialObject GALAXY = body(CelestialObjectId.NOVA_CAELUM, CelestialObject.Class.GALAXY, 0);
    private static final CelestialObject ANCHOR_STAR = body(CelestialObjectId.ILIA, CelestialObject.Class.STAR, 0);
    private static final CelestialObject OTHER_STAR = body(CelestialObjectId.VAEL, CelestialObject.Class.STAR, 0);
    private static final CelestialObject INNER_PLANET = body(
        CelestialObjectId.ROMULUS,
        CelestialObject.Class.PLANET,
        40);
    private static final CelestialObject OUTER_PLANET = body(
        CelestialObjectId.REMUS,
        CelestialObject.Class.PLANET,
        900);

    @Test
    void aGalaxyIsMeasuredByHowFarItsStarsSit() {
        FakeWorld world = new FakeWorld();
        world.children.put(GALAXY, List.of(ANCHOR_STAR, OTHER_STAR));
        world.positions.put(ANCHOR_STAR, new double[] { 300.0, 400.0 });
        world.positions.put(OTHER_STAR, new double[] { 30.0, 40.0 });

        assertEquals(500.0, zoom(world).overviewExtent(GALAXY), 1.0e-9);
    }

    @Test
    void anUnplacedStarDoesNotShrinkTheGalaxyExtent() {
        FakeWorld world = new FakeWorld();
        world.children.put(GALAXY, List.of(ANCHOR_STAR, OTHER_STAR));
        world.positions.put(ANCHOR_STAR, new double[] { 300.0, 400.0 });

        assertEquals(500.0, zoom(world).overviewExtent(GALAXY), 1.0e-9);
    }

    @Test
    void aSystemIsMeasuredByItsWidestChildOrbit() {
        FakeWorld world = new FakeWorld();
        world.children.put(ANCHOR_STAR, List.of(INNER_PLANET, OUTER_PLANET));

        assertEquals(900.0, zoom(world).overviewExtent(ANCHOR_STAR), 1.0e-9);
    }

    @Test
    void aFocusedBodyIsFramedAgainstItsSiblings() {
        FakeWorld world = new FakeWorld();
        world.parents.put(INNER_PLANET, ANCHOR_STAR);
        world.children.put(ANCHOR_STAR, List.of(INNER_PLANET, OUTER_PLANET));

        assertEquals(900.0, zoom(world).focusedOrbitExtent(INNER_PLANET), 1.0e-9);
    }

    @Test
    void aBodyWithoutAParentHasNothingToFrameAgainst() {
        assertEquals(0.0, zoom(new FakeWorld()).focusedOrbitExtent(INNER_PLANET), 1.0e-9);
    }

    @Test
    void departingASystemPullsOutFurtherThanFramingIt() {
        FakeWorld world = new FakeWorld();
        world.children.put(ANCHOR_STAR, List.of(OUTER_PLANET));
        OrbitalBodyZoom bodyZoom = zoom(world);

        assertTrue(
            bodyZoom.systemDepartureZoom(ANCHOR_STAR) < bodyZoom.overviewZoom(ANCHOR_STAR, false),
            "the departure view must be further out than the plain overview");
    }

    @Test
    void theCutLandsCloserInThanTheGalaxyOverviewItEasesOutTo() {
        FakeWorld world = new FakeWorld();
        world.children.put(GALAXY, List.of(ANCHOR_STAR, OTHER_STAR));
        world.positions.put(ANCHOR_STAR, new double[] { 0.0, 0.0 });
        world.positions.put(OTHER_STAR, new double[] { 5_000.0, 0.0 });
        OrbitalBodyZoom bodyZoom = zoom(world);

        assertTrue(bodyZoom.galaxyCutZoom(ANCHOR_STAR) > bodyZoom.galaxyOverviewZoom(ANCHOR_STAR));
    }

    @Test
    void aLoneStarHasNoNeighbourToScaleAgainstSoTheCutFallsBackToTheOverview() {
        FakeWorld world = new FakeWorld();
        world.children.put(GALAXY, List.of(ANCHOR_STAR));
        world.positions.put(ANCHOR_STAR, new double[] { 0.0, 0.0 });
        OrbitalBodyZoom bodyZoom = zoom(world);

        assertEquals(
            bodyZoom.galaxyOverviewZoom(ANCHOR_STAR),
            bodyZoom.galaxyCutZoom(ANCHOR_STAR),
            1.0e-9,
            "with nothing to measure against, the cut and the overview must agree");
    }

    private static OrbitalBodyZoom zoom(FakeWorld world) {
        return new OrbitalBodyZoom(GALAXY, world, new TestStarmapView());
    }

    private static CelestialObject body(CelestialObjectId id, CelestialObject.Class objectClass, double orbitRadius) {
        CelestialObject.Builder builder = CelestialObject.builder()
            .key(id)
            .name(id.name())
            .objectClass(objectClass);
        if (orbitRadius > 0.0) builder.orbitalParams(OrbitalParams.circular(orbitRadius, 1.0));
        return builder.build();
    }

    private static final class FakeWorld implements OrbitalBodyZoom.World {

        final Map<CelestialObject, List<CelestialObject>> children = new HashMap<>();
        final Map<CelestialObject, double[]> positions = new HashMap<>();
        final Map<CelestialObject, CelestialObject> parents = new HashMap<>();

        @Override
        public List<CelestialObject> childrenOf(CelestialObject body) {
            return children.getOrDefault(body, List.of());
        }

        @Override
        public double[] absolutePosition(CelestialObject body) {
            return positions.get(body);
        }

        @Override
        public CelestialObject parentOf(CelestialObject body) {
            return parents.get(body);
        }
    }
}
