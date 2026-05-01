package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

final class RecipeSlotTest {

    // ---------- RecipeSlot record ----------

    @Test
    void recipeSlot_validFields() {
        GT5RecipeRef ref = new GT5RecipeRef((byte) 1, 0, 42L);
        RecipeSlot slot = new RecipeSlot(ref, true, 10, 100, (byte) 5, (byte) 8);
        assertEquals(ref, slot.recipeRef());
        assertTrue(slot.enabled());
        assertEquals(10, slot.inputGuard());
        assertEquals(100, slot.outputGuard());
        assertEquals((byte) 5, slot.priority());
        assertEquals((byte) 8, slot.orderSize());
    }

    @Test
    void recipeSlot_compactConstructor_rejectsNegativeInputGuard() {
        assertThrows(
            IllegalArgumentException.class,
            () -> { new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, -1, 0, (byte) 0, (byte) 1); });
    }

    @Test
    void recipeSlot_compactConstructor_rejectsNegativeOutputGuard() {
        assertThrows(
            IllegalArgumentException.class,
            () -> { new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 0, -1, (byte) 0, (byte) 1); });
    }

    @Test
    void recipeSlot_compactConstructor_rejectsNegativePriority() {
        assertThrows(
            IllegalArgumentException.class,
            () -> { new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 0, 0, (byte) -1, (byte) 1); });
    }

    @Test
    void recipeSlot_compactConstructor_rejectsZeroOrderSize() {
        assertThrows(
            IllegalArgumentException.class,
            () -> { new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 0, 0, (byte) 0, (byte) 0); });
    }

    @Test
    void recipeSlot_compactConstructor_rejectsNegativeOrderSize() {
        assertThrows(
            IllegalArgumentException.class,
            () -> { new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 0, 0, (byte) 0, (byte) -5); });
    }

    @Test
    void recipeSlot_allowsZeroInputGuard() {
        RecipeSlot slot = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 0, 100, (byte) 5, (byte) 8);
        assertEquals(0, slot.inputGuard(), "inputGuard=0 is valid");
    }

    @Test
    void recipeSlot_allowsZeroOutputGuard() {
        RecipeSlot slot = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 10, 0, (byte) 5, (byte) 8);
        assertEquals(0, slot.outputGuard(), "outputGuard=0 is valid (treated as unconfigured by scheduler)");
    }

    @Test
    void recipeSlot_allowsZeroPriority() {
        RecipeSlot slot = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 10, 100, (byte) 0, (byte) 8);
        assertEquals(0, slot.priority(), "priority=0 is valid (lowest priority)");
    }

    @Test
    void recipeSlot_equality_byValue() {
        RecipeSlot a = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 10, 100, (byte) 5, (byte) 8);
        RecipeSlot b = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 10, 100, (byte) 5, (byte) 8);
        assertEquals(a, b, "records must be equal by value");
        assertEquals(a.hashCode(), b.hashCode(), "equal records must have equal hashCodes");
    }

    @Test
    void recipeSlot_inequality_whenFieldDiffers() {
        RecipeSlot a = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 10, 100, (byte) 5, (byte) 8);
        RecipeSlot b = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 99L), true, 10, 100, (byte) 5, (byte) 8);
        assertNotEquals(a, b, "different contentHash in recipeRef should make slots unequal");
    }

    @Test
    void recipeSlot_compactConstructor_rejectsNullRecipeRef() {
        assertThrows(NullPointerException.class, () -> { new RecipeSlot(null, true, 0, 0, (byte) 0, (byte) 1); });
    }

    // ---------- RecipeSlotList ----------

    @Test
    void recipeSlotList_empty_onCreation() {
        RecipeSlotList list = new RecipeSlotList();
        assertEquals(0, list.size());
        assertTrue(list.isEmpty());
    }

    @Test
    void recipeSlotList_addAndGet() {
        RecipeSlotList list = new RecipeSlotList();
        RecipeSlot slot = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 10, 100, (byte) 5, (byte) 8);
        list.add(slot);
        assertEquals(1, list.size());
        assertFalse(list.isEmpty());
        assertSame(slot, list.get(0));
    }

    @Test
    void recipeSlotList_addNull_throws() {
        RecipeSlotList list = new RecipeSlotList();
        assertThrows(NullPointerException.class, () -> list.add(null));
    }

    @Test
    void recipeSlotList_setNull_throws() {
        RecipeSlotList list = new RecipeSlotList();
        assertThrows(NullPointerException.class, () -> list.set(0, null));
    }

    @Test
    void recipeSlotList_set_replacesSlot() {
        RecipeSlotList list = new RecipeSlotList();
        RecipeSlot slot1 = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 10, 100, (byte) 5, (byte) 8);
        RecipeSlot slot2 = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 99L), false, 5, 50, (byte) 1, (byte) 3);
        list.add(slot1);
        assertSame(slot1, list.get(0));
        list.set(0, slot2);
        assertEquals(1, list.size(), "size should not change on set");
        assertSame(slot2, list.get(0));
    }

    @Test
    void recipeSlotList_get_outOfBounds_throws() {
        RecipeSlotList list = new RecipeSlotList();
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(0));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(32));
    }

    @Test
    void recipeSlotList_set_outOfBounds_throws() {
        RecipeSlotList list = new RecipeSlotList();
        RecipeSlot slot = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 10, 100, (byte) 5, (byte) 8);
        assertThrows(IndexOutOfBoundsException.class, () -> list.set(-1, slot));
        assertThrows(IndexOutOfBoundsException.class, () -> list.set(32, slot));
    }

    @Test
    void recipeSlotList_remove_nullifiesSlot() {
        RecipeSlotList list = new RecipeSlotList();
        RecipeSlot slot = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 10, 100, (byte) 5, (byte) 8);
        list.add(slot);
        assertEquals(1, list.size());
        list.remove(0);
        assertTrue(list.isEmpty());
        assertEquals(0, list.size());
    }

    @Test
    void recipeSlotList_remove_outOfBounds_throws() {
        RecipeSlotList list = new RecipeSlotList();
        assertThrows(IndexOutOfBoundsException.class, () -> list.remove(0));
        assertThrows(IndexOutOfBoundsException.class, () -> list.remove(-1));
    }

    @Test
    void recipeSlotList_maxSizeBoundary() {
        RecipeSlotList list = new RecipeSlotList();
        RecipeSlot slot = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 10, 100, (byte) 5, (byte) 8);
        for (int i = 0; i < RecipeSlotList.MAX_RECIPE_SLOTS; i++) {
            list.add(slot);
        }
        assertEquals(RecipeSlotList.MAX_RECIPE_SLOTS, list.size());

        // 33rd add should throw
        assertThrows(IllegalStateException.class, () -> list.add(slot));
    }

    @Test
    void recipeSlotList_forEach_iteratesOnlyNonNull() {
        RecipeSlotList list = new RecipeSlotList();
        RecipeSlot s1 = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 10, 100, (byte) 5, (byte) 8);
        RecipeSlot s2 = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 99L), false, 5, 50, (byte) 1, (byte) 3);
        list.add(s1);
        list.add(s2);
        list.remove(0);

        int[] count = { 0 };
        list.forEach(slot -> count[0]++);
        assertEquals(1, count[0], "only s2 should remain after removing index 0");
    }

    @Test
    void recipeSlotList_toList_returnsImmutableCopy() {
        RecipeSlotList list = new RecipeSlotList();
        RecipeSlot s1 = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 10, 100, (byte) 5, (byte) 8);
        RecipeSlot s2 = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 99L), false, 5, 50, (byte) 1, (byte) 3);
        list.add(s1);
        list.add(s2);

        List<RecipeSlot> snapshot = list.toList();
        assertEquals(2, snapshot.size());
        assertSame(s1, snapshot.get(0));
        assertSame(s2, snapshot.get(1));

        // Mutation on list should not affect snapshot
        list.remove(0);
        assertEquals(2, snapshot.size(), "snapshot should be immutable copy");
        assertEquals(1, list.size());

        // Snapshot itself should be immutable
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(s1));
    }

    @Test
    void recipeSlotList_toList_skipsNullSlots() {
        RecipeSlotList list = new RecipeSlotList();
        RecipeSlot slot = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 10, 100, (byte) 5, (byte) 8);
        list.add(slot);
        list.remove(0);
        assertTrue(
            list.toList()
                .isEmpty(),
            "toList should skip null slots");
    }

    @Test
    void recipeSlotList_maxSizeConstant_is32() {
        assertEquals(32, RecipeSlotList.MAX_RECIPE_SLOTS);
    }

    @Test
    void recipeSlotList_fillsSequentially() {
        RecipeSlotList list = new RecipeSlotList();
        RecipeSlot template = new RecipeSlot(new GT5RecipeRef((byte) 1, 0, 42L), true, 10, 100, (byte) 5, (byte) 8);
        for (int i = 0; i < RecipeSlotList.MAX_RECIPE_SLOTS; i++) {
            list.add(template);
        }
        // Verify sequential fill: indices 0..31 are non-null
        for (int i = 0; i < RecipeSlotList.MAX_RECIPE_SLOTS; i++) {
            assertNotNull(list.get(i), "slot at index " + i + " should be non-null after sequential add");
        }
    }
}
