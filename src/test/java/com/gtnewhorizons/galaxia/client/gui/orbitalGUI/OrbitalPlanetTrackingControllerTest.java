package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class OrbitalPlanetTrackingControllerTest {

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
