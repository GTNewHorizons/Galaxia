package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class RecipeBookSchedulingTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    @Test
    void prioritySelectsTheHighestEnabledPriority() {
        RecipeBook book = book(
            RecipeSchedulerMode.PRIORITY,
            recipe(0, true, 1, 1),
            recipe(1, false, 9, 1),
            recipe(2, true, 5, 1));

        RecipeBook.Selection selection = book.select(RecipeScheduleState.RESET, new Random(0))
            .orElseThrow();

        assertEquals(2, selection.index());
    }

    @Test
    void orderStateAdvancesOnlyThroughTheExplicitSuccessBoundary() {
        RecipeBook book = book(RecipeSchedulerMode.ORDER, recipe(0, true, 1, 3), recipe(1, true, 1, 2));
        RecipeScheduleState initial = new RecipeScheduleState((byte) 0, (byte) 3);
        RecipeBook.Selection selection = book.select(initial, new Random(0))
            .orElseThrow();

        assertEquals(0, selection.index());
        assertEquals(initial, new RecipeScheduleState((byte) 0, (byte) 3));
        assertEquals(new RecipeScheduleState((byte) 0, (byte) 2), book.advanceAfterSuccess(initial, selection));
    }

    @Test
    void exhaustedOrderStateSkipsDisabledRecipesAndWraps() {
        RecipeBook book = book(
            RecipeSchedulerMode.ORDER,
            recipe(0, true, 1, 3),
            recipe(1, false, 1, 2),
            recipe(2, true, 1, 4));
        RecipeScheduleState state = new RecipeScheduleState((byte) 0, (byte) 0);
        RecipeBook.Selection selection = book.select(state, new Random(0))
            .orElseThrow();

        assertEquals(2, selection.index());
        assertEquals(new RecipeScheduleState((byte) 2, (byte) 4), book.advanceAfterSuccess(state, selection));
    }

    @Test
    void randomSelectionNeverReturnsDisabledRecipes() {
        RecipeBook book = book(
            RecipeSchedulerMode.RANDOM,
            recipe(0, false, 1, 1),
            recipe(1, true, 1, 1),
            recipe(2, true, 1, 1));
        Random random = new Random(42);

        for (int i = 0; i < 20; i++) {
            int selected = book.select(RecipeScheduleState.RESET, random)
                .orElseThrow()
                .index();
            assertTrue(selected == 1 || selected == 2);
        }
    }

    @Test
    void nonOrderModesKeepTheSameScheduleValueAfterSuccess() {
        RecipeBook book = book(RecipeSchedulerMode.PRIORITY, recipe(0, true, 1, 1));
        RecipeScheduleState state = new RecipeScheduleState((byte) 4, (byte) 2);
        RecipeBook.Selection selection = book.select(state, new Random(0))
            .orElseThrow();

        assertSame(state, book.advanceAfterSuccess(state, selection));
    }

    private static RecipeBook book(RecipeSchedulerMode mode, SavedRecipe... recipes) {
        return new RecipeBook(List.of(recipes), mode, NotDoablePolicy.SKIP);
    }

    private static SavedRecipe recipe(int index, boolean enabled, int priority, int orderSize) {
        RecipeSnapshot snapshot = RecipeSnapshot.resolved(
            (byte) 1,
            index,
            new ItemStack[] { new ItemStack(new Item()) },
            new ItemStack[] { new ItemStack(new Item()) },
            null,
            null,
            20,
            30);
        return new SavedRecipe(snapshot, enabled, 0L, (byte) priority, (byte) orderSize);
    }
}
