package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.Engine;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.Kind;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.Storage;

final class RocketBlueprintTest {

    @Test
    void editingCopiedBlueprintPreservesOriginalDesign() {
        var part = new RocketPartInstance(RocketPartDef.structural(1, "frame", 1, 1, 1, null), 0, 0, 0, false);
        RocketBlueprint original = new RocketBlueprint();
        original.setName("original");
        assertTrue(original.addPart(part));

        RocketBlueprint edited = original.copy();
        edited.removePartAt(0, 0, 0);
        edited.setName("edited");

        assertTrue(edited.isEmpty());
        assertEquals("edited", edited.getName());
        assertEquals(List.of(part), original.getParts());
        assertEquals("original", original.getName());
    }

    @Test
    void structuralViabilityDoesNotAssumeFilledTanksOrClaimFlightCapability() {
        var emptyStorage = new Storage(0, 0, java.util.Map.of());
        var command = new RocketPartDef(
            10,
            "test.command",
            1,
            1,
            450,
            null,
            Kind.CAPSULE,
            0,
            0,
            emptyStorage,
            null,
            null,
            null);
        var engine = new RocketPartDef(
            11,
            "test.engine",
            1,
            1,
            250,
            null,
            Kind.ENGINE,
            0,
            0,
            emptyStorage,
            new Engine(1, 3200, java.util.Map.of()),
            null,
            null);
        var blueprint = new RocketBlueprint();
        blueprint.addPart(new RocketPartInstance(command, 0, 0, 0, false));

        assertFalse(
            blueprint.analyze()
                .viable());

        blueprint.addPart(new RocketPartInstance(engine, 0, 1, 0, false));

        assertTrue(
            blueprint.analyze()
                .viable());
        assertEquals(
            700,
            blueprint.analyze()
                .getTotalDryMassKg());
    }
}
