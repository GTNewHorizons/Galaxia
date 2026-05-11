package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.item.Item;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

final class RecipeSlotBoundsTest {

    @Test
    void emptyReturnsSingletonInstance() {
        assertSame(RecipeSlotBounds.empty(), RecipeSlotBounds.empty());
    }

    @Test
    void withInputItemLowerBoundReturnsSameInstanceWhenValueUnchanged() {
        ItemStackWrapper item = new ItemStackWrapper(new Item(), 0, null);
        RecipeSlotBounds bounds = RecipeSlotBounds.empty()
            .withInputItemLowerBound(item, 64);

        assertSame(bounds, bounds.withInputItemLowerBound(item, 64));
    }

    @Test
    void withOutputItemUpperBoundReturnsSameInstanceWhenValueUnchanged() {
        ItemStackWrapper item = new ItemStackWrapper(new Item(), 0, null);
        RecipeSlotBounds bounds = RecipeSlotBounds.empty()
            .withOutputItemUpperBound(item, 128);

        assertSame(bounds, bounds.withOutputItemUpperBound(item, 128));
    }
}
