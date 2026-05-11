package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.Item;

import org.junit.jupiter.api.Test;

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

    private static ItemStackWrapper resource() {
        return new ItemStackWrapper(new Item(), 0, null);
    }

    private static long trackedTotalItems(AutomatedFacilityInventory inventory) throws Exception {
        Field field = AutomatedFacilityInventory.class.getDeclaredField("totalItemAmount");
        field.setAccessible(true);
        return field.getLong(inventory);
    }
}
