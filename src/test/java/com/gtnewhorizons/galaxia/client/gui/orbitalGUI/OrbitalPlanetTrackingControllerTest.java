package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class OrbitalPlanetTrackingControllerTest {

    @Test
    void disabledHierarchyClickTracksPlanetWithoutOpeningHierarchy() {
        CelestialObject planet = body(CelestialObjectId.OVERWORLD, "Overworld", CelestialObject.Class.PLANET);
        OrbitalPlanetTrackingController controller = new OrbitalPlanetTrackingController();
        controller.setDisableHierarchicalView(true);

        OrbitalPlanetTrackingController.ClickAction action = controller.clickBody(planet, true);

        assertEquals(OrbitalPlanetTrackingController.ClickAction.TRACK_ONLY, action);
        assertTrue(controller.isFollowing());
        assertSame(planet, controller.focusedBody());
    }

    @Test
    void normalClickAllowsHierarchySelection() {
        CelestialObject star = body(CelestialObjectId.ILIA, "Ilia", CelestialObject.Class.STAR);
        OrbitalPlanetTrackingController controller = new OrbitalPlanetTrackingController();

        OrbitalPlanetTrackingController.ClickAction action = controller.clickBody(star, true);

        assertEquals(OrbitalPlanetTrackingController.ClickAction.SELECT_ONLY, action);
    }

    @Test
    void scrollDoesNotUnlockTrackingButManualMovementDoes() {
        CelestialObject planet = body(CelestialObjectId.MARS, "Mars", CelestialObject.Class.PLANET);
        OrbitalPlanetTrackingController controller = new OrbitalPlanetTrackingController();
        controller.setDisableHierarchicalView(true);
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
            .id(id)
            .name(name)
            .objectClass(objectClass)
            .build();
    }
}
