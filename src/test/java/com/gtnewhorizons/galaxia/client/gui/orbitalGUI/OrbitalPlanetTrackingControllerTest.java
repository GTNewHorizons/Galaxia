package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class OrbitalPlanetTrackingControllerTest {

    /** Bug regression: selecting a moving body preserves its screen position briefly instead of snapping. */
    @Test
    void followCameraPreservesTheDoubleClickWindow() {
        var view = new OrbitalView.OrbitalViewState(0);
        view.setCamera(10, 20);
        view.beginFollowing(110, 220, 0);
        view.step(0.045);
        view.follow(115, 230, 50_000_000L);
        assertEquals(100, 115 - view.cameraX, 1e-9);
        assertEquals(200, 230 - view.cameraY, 1e-9);
        view.follow(115, 230, 110_000_000L);
        assertTrue(view.cameraX > 15 && view.cameraX < 25);
    }

    /** Product contract: camera centering accelerates and decelerates, then follows the body without lag. */
    @Test
    void followCameraEasesThenLocksToTheMovingTarget() {
        var view = new OrbitalView.OrbitalViewState(0);
        view.beginFollowing(100, 0, 0);
        double[] positions = new double[6];
        for (int i = 0; i < positions.length; i++) {
            view.follow(100, 0, (100L + 50L * i) * 1_000_000L);
            positions[i] = view.cameraX;
        }
        assertTrue(positions[1] - positions[0] < positions[2] - positions[1]);
        assertTrue(positions[4] - positions[3] > positions[5] - positions[4]);
        for (int i = 1; i < positions.length; i++) assertTrue(positions[i] >= positions[i - 1]);
        view.follow(120, 30, 1_000_000_000L);
        assertEquals(120, view.cameraX, 1e-9);
        assertEquals(30, view.cameraY, 1e-9);
    }

    /** Product contract: changing the followed body during centering starts from the current camera position. */
    @Test
    void followCameraCanRetargetWithoutJumping() {
        var view = new OrbitalView.OrbitalViewState(0);
        view.beginFollowing(100, 0, 0);
        view.follow(100, 0, 250_000_000L);
        double before = view.cameraX;
        view.beginFollowing(-100, 0, 250_000_000L);
        view.follow(-100, 0, 250_000_000L);
        assertEquals(before, view.cameraX, 1e-9);
    }

    @Test
    void clickingSelectedStarEntersItsSystem() {
        var star = body(CelestialObjectId.VEGA, "Vega", CelestialObject.Class.STAR);
        var other = body(CelestialObjectId.SOL, "Sol", CelestialObject.Class.STAR);
        var controller = new OrbitalPlanetTrackingController();
        assertEquals(OrbitalPlanetTrackingController.ClickAction.TRACK_ONLY, controller.clickBody(star, true));
        assertEquals(OrbitalPlanetTrackingController.ClickAction.SELECT_ONLY, controller.clickBody(star, true));
        assertEquals(OrbitalPlanetTrackingController.ClickAction.TRACK_ONLY, controller.clickBody(other, true));
        assertEquals(OrbitalPlanetTrackingController.ClickAction.TRACK_ONLY, controller.clickBody(other, false));
    }

    @Test
    void defaultClickTracksPlanetWithoutOpeningHierarchy() {
        CelestialObject planet = body(CelestialObjectId.OVERWORLD, "Overworld", CelestialObject.Class.PLANET);
        OrbitalPlanetTrackingController controller = new OrbitalPlanetTrackingController();

        OrbitalPlanetTrackingController.ClickAction action = controller.clickBody(planet, true);

        assertEquals(OrbitalPlanetTrackingController.ClickAction.TRACK_ONLY, action);
        assertTrue(controller.isFollowing());
        assertSame(planet, controller.focusedBody());
    }

    @Test
    void explicitlySelectedHierarchyModeAllowsHierarchySelection() {
        CelestialObject star = body(CelestialObjectId.ILIA, "Ilia", CelestialObject.Class.STAR);
        OrbitalPlanetTrackingController controller = new OrbitalPlanetTrackingController();
        controller.setClickMode(OrbitalMapClickMode.HIERARCHY);

        OrbitalPlanetTrackingController.ClickAction action = controller.clickBody(star, true);

        assertEquals(OrbitalPlanetTrackingController.ClickAction.SELECT_ONLY, action);
    }

    @Test
    void scrollDoesNotUnlockTrackingButManualMovementDoes() {
        CelestialObject planet = body(CelestialObjectId.MARS, "Mars", CelestialObject.Class.PLANET);
        OrbitalPlanetTrackingController controller = new OrbitalPlanetTrackingController();
        controller.setClickMode(OrbitalMapClickMode.FOLLOW);
        controller.clickBody(planet, false);

        controller.onScrolled();

        assertTrue(controller.isFollowing());
        assertSame(planet, controller.focusedBody());

        controller.onManualCameraMoved();

        assertFalse(controller.isFollowing());
        assertSame(planet, controller.focusedBody());
    }

    private static CelestialObject body(CelestialObjectId id, String name, CelestialObject.Class objectClass) {
        return CelestialObject.builder()
            .key(id)
            .name(name)
            .objectClass(objectClass)
            .build();
    }
}
