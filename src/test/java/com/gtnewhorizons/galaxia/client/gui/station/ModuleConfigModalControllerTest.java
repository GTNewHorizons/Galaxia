package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

final class ModuleConfigModalControllerTest {

    @Test
    void moduleOperationCancelConfirmationCanBeArmedAndCleared() {
        ModuleConfigModalController controller = new ModuleConfigModalController(null, CelestialAsset.ID.create(), 0, 0);

        assertFalse(controller.isModuleOperationCancelArmed());

        controller.armModuleOperationCancel();

        assertTrue(controller.isModuleOperationCancelArmed());

        controller.clearModuleOperationCancel();

        assertFalse(controller.isModuleOperationCancelArmed());
    }
}
