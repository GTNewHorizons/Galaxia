package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.StructuralPartDef;

final class RocketBlueprintTest {

    @Test
    void editingCopiedBlueprintPreservesOriginalDesign() {
        var part = new RocketPartInstance(new StructuralPartDef(1, "frame", 1, 1, 1, null), 0, 0, 0, false);
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
}
