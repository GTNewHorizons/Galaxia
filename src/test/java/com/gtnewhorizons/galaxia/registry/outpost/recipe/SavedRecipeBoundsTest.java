package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeBounds.Kind;

final class SavedRecipeBoundsTest {

    @Test
    void emptyReturnsSingletonInstance() {
        assertSame(SavedRecipeBounds.empty(), SavedRecipeBounds.empty());
    }

    @Test
    void withBoundReturnsSameInstanceWhenValueUnchanged() {
        SavedRecipeBounds bounds = SavedRecipeBounds.empty()
            .withBound(Kind.INPUT_ITEM_LOWER, 2, 64);

        assertSame(bounds, bounds.withBound(Kind.INPUT_ITEM_LOWER, 2, 64));
    }

    @Test
    void withoutBoundReturnsSameInstanceWhenMissing() {
        SavedRecipeBounds bounds = SavedRecipeBounds.empty()
            .withBound(Kind.OUTPUT_ITEM_UPPER, 3, 128);

        assertSame(bounds, bounds.withoutBound(Kind.OUTPUT_ITEM_UPPER, 4));
    }

    @Test
    void canonicalizesDuplicateItemSlotsWithinSameBoundKind() {
        Item duplicate = new Item();
        RecipeSnapshot recipe = RecipeSnapshot.resolved(
            (byte) 1,
            0,
            new ItemStack[] { new ItemStack(duplicate, 1, 0), new ItemStack(duplicate, 2, 0) },
            null,
            null,
            null,
            20,
            30);

        SavedRecipeBounds bounds = SavedRecipeBounds.empty()
            .withBound(recipe, Kind.INPUT_ITEM_LOWER, 1, 64);

        assertTrue(bounds.hasBound(Kind.INPUT_ITEM_LOWER, 0));
        assertTrue(bounds.hasBound(recipe, Kind.INPUT_ITEM_LOWER, 1));
        assertFalse(bounds.hasBound(Kind.INPUT_ITEM_LOWER, 1));
        assertEquals(64, bounds.boundOrDefault(recipe, Kind.INPUT_ITEM_LOWER, 1));
    }

    @Test
    void savedRecipeCanonicalizesIncomingDuplicateItemBounds() {
        Item duplicate = new Item();
        RecipeSnapshot recipe = RecipeSnapshot.resolved(
            (byte) 1,
            0,
            new ItemStack[] { new ItemStack(duplicate, 1, 0), new ItemStack(duplicate, 2, 0) },
            null,
            null,
            null,
            20,
            30);
        SavedRecipeBounds nonCanonical = SavedRecipeBounds.empty()
            .withBound(Kind.INPUT_ITEM_LOWER, 1, 64);

        SavedRecipe savedRecipe = new SavedRecipe(recipe, true, nonCanonical, (byte) 1, (byte) 1);

        assertTrue(
            savedRecipe.bounds()
                .hasBound(Kind.INPUT_ITEM_LOWER, 0));
        assertFalse(
            savedRecipe.bounds()
                .hasBound(Kind.INPUT_ITEM_LOWER, 1));
    }
}
