package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.Item;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlotBounds;

final class AutomatedFacilityInventoryTest {

    @Test
    void totalItemsTracksMutationsAndClearsState() throws Exception {
        AutomatedFacilityInventory inventory = new AutomatedFacilityInventory();
        ItemStackWrapper first = resource();
        ItemStackWrapper second = resource();

        inventory.add(first, 5);
        inventory.add(second, 7);
        inventory.tryConsume(first, 2);
        inventory.setAmount(second, 3);
        inventory.add(first, -3);

        assertEquals(3L, inventory.totalItems());
        assertEquals(3L, trackedTotalItems(inventory));
        assertEquals(0L, inventory.getAmount(first));
        assertEquals(3L, inventory.getAmount(second));

        Map<ItemStackWrapper, Long> snapshot = new LinkedHashMap<>();
        snapshot.put(first, 4L);
        snapshot.put(second, 9L);
        inventory.loadFromSnapshot(snapshot);

        assertEquals(13L, inventory.totalItems());
        assertEquals(13L, trackedTotalItems(inventory));

        inventory.clear();

        assertEquals(0L, inventory.totalItems());
        assertEquals(0L, trackedTotalItems(inventory));
    }

    @Test
    void recipeBoundsAreCheckedAgainstPostOperationInventoryAmounts() {
        AutomatedFacilityInventory inventory = new AutomatedFacilityInventory();
        ItemStackWrapper input = resource();
        ItemStackWrapper output = resource();
        inventory.add(input, 40);
        inventory.add(output, 990);

        RecipeSlotBounds bounds = RecipeSlotBounds.empty()
            .withInputItemLowerBound(input, 32)
            .withOutputItemUpperBound(output, 1000);

        assertTrue(inventory.keepsItemLowerBoundsAfterConsume(Map.of(input, 8L), bounds));
        assertFalse(inventory.keepsItemLowerBoundsAfterConsume(Map.of(input, 9L), bounds));
        assertTrue(inventory.acceptsItemUpperBoundsAfterInsert(Map.of(output, 10L), bounds));
        assertFalse(inventory.acceptsItemUpperBoundsAfterInsert(Map.of(output, 11L), bounds));
    }

    @Test
    void recipeFluidBoundsAreCheckedAgainstPostOperationInventoryAmounts() {
        AutomatedFacilityInventory inventory = new AutomatedFacilityInventory();
        inventory.addFluid("input", 1000);
        inventory.addFluid("output", 900);

        RecipeSlotBounds bounds = RecipeSlotBounds.empty()
            .withInputFluidLowerBound("input", 800)
            .withOutputFluidUpperBound("output", 1000);

        assertTrue(inventory.keepsFluidLowerBoundsAfterConsume(Map.of("input", 200L), bounds));
        assertFalse(inventory.keepsFluidLowerBoundsAfterConsume(Map.of("input", 201L), bounds));
        assertTrue(inventory.acceptsFluidUpperBoundsAfterInsert(Map.of("output", 100L), bounds));
        assertFalse(inventory.acceptsFluidUpperBoundsAfterInsert(Map.of("output", 101L), bounds));
    }

    private static ItemStackWrapper resource() {
        return new ItemStackWrapper(new Item(), 0, null);
    }

    private static long trackedTotalItems(AutomatedFacilityInventory inventory) throws Exception {
        Field field = AutomatedFacilityInventory.class.getDeclaredField("totalItemAmount");
        field.setAccessible(true);
        return field.getLong(inventory);
    }
}
