package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

final class RecipeSlotPayloadValidatorTest {

    @Test
    void rejectsNullModule() {
        RecipeSnapshot snapshot = RecipeSnapshot.unresolved((byte) 1, 0, 123L);

        assertNull(RecipeSlotPayloadValidator.validate(null, snapshot));
    }

    @Test
    void rejectsNullSnapshot() {
        assertNull(RecipeSlotPayloadValidator.validate(null, null));
    }

    @Test
    void rejectsInvalidRecipeMapOrdinal() {
        RecipeSnapshot snapshot = RecipeSnapshot.unresolved((byte) 99, 0, 123L);

        assertNull(RecipeSlotPayloadValidator.validate(null, snapshot));
    }
}
