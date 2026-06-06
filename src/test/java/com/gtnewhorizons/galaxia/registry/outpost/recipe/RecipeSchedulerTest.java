package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

final class RecipeSchedulerTest {

    private static SavedRecipe slot(boolean enabled, byte priority, byte orderSize) {
        return new SavedRecipe(RecipeSnapshot.unresolved((byte) 1, 0, 42L), enabled, 0L, priority, orderSize);
    }

    // ---------- PRIORITY mode ----------

    @Test
    void nextSlotPriority_returnsHighestPriority() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(true, (byte) 1, (byte) 1));
        slots.add(slot(true, (byte) 5, (byte) 1));
        slots.add(slot(true, (byte) 3, (byte) 1));
        int idx = RecipeScheduler.nextSlotPriority(slots);
        assertEquals(1, idx, "slot at index 1 has highest priority 5");
    }

    @Test
    void nextSlotPriority_returnsNegativeOne_whenAllDisabled() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(false, (byte) 1, (byte) 1));
        slots.add(slot(false, (byte) 5, (byte) 1));
        assertEquals(-1, RecipeScheduler.nextSlotPriority(slots));
    }

    @Test
    void nextSlotPriority_returnsNegativeOne_whenEmpty() {
        SavedRecipeList slots = new SavedRecipeList();
        assertEquals(-1, RecipeScheduler.nextSlotPriority(slots));
    }

    // ---------- ORDER mode ----------

    @Test
    void nextSlotOrder_returnsCurrentCursor_whenRemainingGreaterThanZero() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(true, (byte) 0, (byte) 5));
        slots.add(slot(true, (byte) 0, (byte) 3));
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP,
            (byte) 1,
            (byte) 2);
        int idx = RecipeScheduler.nextSlotOrder(config);
        assertEquals(1, idx, "should return cursor when remaining > 0");
    }

    @Test
    void nextSlotOrder_findsNextEnabledSlot_afterCursor_whenRemainingExhausted() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(true, (byte) 0, (byte) 5)); // index 0
        slots.add(slot(true, (byte) 0, (byte) 3)); // index 1
        slots.add(slot(true, (byte) 0, (byte) 2)); // index 2
        // cursor=1, remaining=0 → should find next enabled after 1 → index 2
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP,
            (byte) 1,
            (byte) 0);
        int idx = RecipeScheduler.nextSlotOrder(config);
        assertEquals(2, idx, "should find next enabled slot after cursor");
    }

    @Test
    void nextSlotOrder_wrapsAround_whenAtEnd() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(true, (byte) 0, (byte) 5)); // index 0
        slots.add(slot(true, (byte) 0, (byte) 3)); // index 1
        // cursor=1, remaining=0 → next enabled after 1 wraps to 0
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP,
            (byte) 1,
            (byte) 0);
        int idx = RecipeScheduler.nextSlotOrder(config);
        assertEquals(0, idx, "should wrap to first enabled slot");
    }

    @Test
    void nextSlotOrder_skipsDisabledSlots() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(true, (byte) 0, (byte) 5)); // index 0 - enabled
        slots.add(slot(false, (byte) 0, (byte) 3)); // index 1 - disabled
        slots.add(slot(true, (byte) 0, (byte) 2)); // index 2 - enabled
        // cursor=0, remaining=0 → should skip index 1 (disabled), find index 2
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP,
            (byte) 0,
            (byte) 0);
        int idx = RecipeScheduler.nextSlotOrder(config);
        assertEquals(2, idx, "should skip disabled slots");
    }

    @Test
    void nextSlotOrder_returnsNegativeOne_whenNoEnabledSlots() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(false, (byte) 0, (byte) 5));
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP,
            (byte) 0,
            (byte) 0);
        assertEquals(-1, RecipeScheduler.nextSlotOrder(config));
    }

    // ---------- RANDOM mode ----------

    @Test
    void nextSlotRandom_returnsIndexWithinEnabledSlots() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(false, (byte) 0, (byte) 1)); // index 0 - disabled
        slots.add(slot(true, (byte) 0, (byte) 1)); // index 1 - enabled
        slots.add(slot(true, (byte) 0, (byte) 1)); // index 2 - enabled
        Random rng = new Random(42);
        // With seed 42, verify it returns 1 or 2 (only enabled indices)
        for (int i = 0; i < 20; i++) {
            int idx = RecipeScheduler.nextSlotRandom(slots, rng);
            assertTrue(idx == 1 || idx == 2, "random pick must be among enabled slots, got " + idx);
        }
    }

    @Test
    void nextSlotRandom_returnsNegativeOne_whenNoEnabledSlots() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(false, (byte) 0, (byte) 1));
        assertEquals(-1, RecipeScheduler.nextSlotRandom(slots, new Random(42)));
    }

    @Test
    void nextSlotRandom_returnsNegativeOne_whenEmpty() {
        SavedRecipeList slots = new SavedRecipeList();
        assertEquals(-1, RecipeScheduler.nextSlotRandom(slots, new Random(42)));
    }

    @Test
    void nextSlotRandom_returnsSpecificSlot_whenOnlyOneEnabled() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(true, (byte) 0, (byte) 1));
        int idx = RecipeScheduler.nextSlotRandom(slots, new Random(42));
        assertEquals(0, idx, "only one enabled slot, must be selected");
    }

    // ---------- dispatch ----------

    @Test
    void nextSlot_dispatchesToPriority() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(true, (byte) 1, (byte) 1));
        slots.add(slot(true, (byte) 5, (byte) 1));
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.PRIORITY,
            NotDoablePolicy.SKIP,
            (byte) 0,
            (byte) 0);
        assertEquals(1, RecipeScheduler.nextSlot(config, new Random(0)));
    }

    @Test
    void nextSlot_dispatchesToOrder() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(true, (byte) 0, (byte) 5));
        slots.add(slot(true, (byte) 0, (byte) 3));
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP,
            (byte) 1,
            (byte) 2);
        assertEquals(1, RecipeScheduler.nextSlot(config, new Random(0)));
    }

    @Test
    void nextSlot_dispatchesToRandom() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(true, (byte) 0, (byte) 1));
        slots.add(slot(true, (byte) 0, (byte) 1));
        // We use a deterministic Random but we can't predict which index;
        // just verify it's >= 0 and < 2
        int idx = RecipeScheduler.nextSlot(
            new RecipeConfig(slots, RecipeSchedulerMode.RANDOM, NotDoablePolicy.SKIP, (byte) 0, (byte) 0),
            new Random(42));
        assertTrue(idx >= 0 && idx < 2, "random pick must be valid index");
    }

    @Test
    void nextSlot_returnsNegativeOne_forEmptySlotsRegardlessOfMode() {
        SavedRecipeList slots = new SavedRecipeList();
        for (RecipeSchedulerMode mode : RecipeSchedulerMode.values()) {
            RecipeConfig config = new RecipeConfig(slots, mode, NotDoablePolicy.SKIP, (byte) 0, (byte) 0);
            assertEquals(
                -1,
                RecipeScheduler.nextSlot(config, new Random(0)),
                "mode " + mode + " with empty slots must return -1");
        }
    }

    // ---------- advanceOrder ----------

    @Test
    void advanceOrder_advancesCursor() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(true, (byte) 0, (byte) 5)); // index 0
        slots.add(slot(true, (byte) 0, (byte) 3)); // index 1
        slots.add(slot(true, (byte) 0, (byte) 2)); // index 2
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP,
            (byte) 0,
            (byte) 0);
        RecipeConfig advanced = RecipeScheduler.advanceOrder(config);
        assertEquals((byte) 1, advanced.orderCursor(), "cursor should advance to next enabled slot");
        assertEquals((byte) 3, advanced.orderRemaining(), "remaining should be orderSize of new slot");
    }

    @Test
    void advanceOrder_wrapsAround() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(true, (byte) 0, (byte) 5)); // index 0
        slots.add(slot(true, (byte) 0, (byte) 3)); // index 1
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP,
            (byte) 1,
            (byte) 0);
        RecipeConfig advanced = RecipeScheduler.advanceOrder(config);
        assertEquals((byte) 0, advanced.orderCursor(), "cursor should wrap to first enabled slot");
        assertEquals((byte) 5, advanced.orderRemaining(), "remaining should be orderSize of wrapped slot");
    }

    @Test
    void advanceOrder_skipsDisabledSlots() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(true, (byte) 0, (byte) 5)); // index 0 - enabled
        slots.add(slot(false, (byte) 0, (byte) 3)); // index 1 - disabled
        slots.add(slot(true, (byte) 0, (byte) 2)); // index 2 - enabled
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP,
            (byte) 0,
            (byte) 0);
        RecipeConfig advanced = RecipeScheduler.advanceOrder(config);
        assertEquals((byte) 2, advanced.orderCursor(), "should skip disabled slot at index 1");
    }

    @Test
    void advanceOrder_returnsSameConfig_whenNoEnabledSlots() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(false, (byte) 0, (byte) 5));
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP,
            (byte) 0,
            (byte) 0);
        RecipeConfig advanced = RecipeScheduler.advanceOrder(config);
        assertSame(config, advanced, "should return same config when no enabled slots");
    }

    @Test
    void advanceOrder_staysOnOnlyEnabledSlot() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(true, (byte) 0, (byte) 5)); // index 0 - only enabled
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP,
            (byte) 0,
            (byte) 0);
        RecipeConfig advanced = RecipeScheduler.advanceOrder(config);
        assertEquals((byte) 0, advanced.orderCursor(), "cursor should stay on the only enabled slot");
        assertEquals((byte) 5, advanced.orderRemaining());
    }

    @Test
    void advanceOrder_decrementsRemaining_runsOrderSizeTimes() {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(slot(true, (byte) 0, (byte) 3)); // orderSize=3
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP,
            (byte) 0,
            (byte) 3);

        // First call: remaining 3 → decrement to 2, same cursor
        RecipeConfig c1 = RecipeScheduler.advanceOrder(config);
        assertEquals((byte) 0, c1.orderCursor(), "cursor should not advance yet");
        assertEquals((byte) 2, c1.orderRemaining(), "remaining should decrement to 2");

        // Second call: remaining 2 → decrement to 1, same cursor
        RecipeConfig c2 = RecipeScheduler.advanceOrder(c1);
        assertEquals((byte) 0, c2.orderCursor(), "cursor should not advance yet");
        assertEquals((byte) 1, c2.orderRemaining(), "remaining should decrement to 1");

        // Third call: remaining 1 → advance to next enabled slot
        RecipeConfig c3 = RecipeScheduler.advanceOrder(c2);
        assertEquals((byte) 0, c3.orderCursor(), "cursor should wrap to first enabled slot (only one)");
        assertEquals((byte) 3, c3.orderRemaining(), "remaining should reset to slot's orderSize");
    }
}
