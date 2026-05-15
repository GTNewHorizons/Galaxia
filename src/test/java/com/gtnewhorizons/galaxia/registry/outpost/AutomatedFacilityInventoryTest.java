package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.item.Item;
import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory.FluidKey;

final class AutomatedFacilityInventoryTest {

    private static final FluidKey INPUT_KEY = new FluidKey(new Fluid("input"), null);
    private static final FluidKey OUTPUT_KEY = new FluidKey(new Fluid("output"), null);

    @Test
    void recipeBoundsCheckLowerReserveAndUpperTargetInventoryAmounts() {
        AutomatedFacilityInventory inventory = new AutomatedFacilityInventory();
        ItemStackWrapper input = resource();
        ItemStackWrapper output = resource();
        inventory.add(input, 40);
        inventory.add(output, 990);

        assertTrue(inventory.keepsItemLowerBoundAfterConsume(input, 8L, 32L));
        assertFalse(inventory.keepsItemLowerBoundAfterConsume(input, 9L, 32L));
        assertTrue(inventory.isItemBelowUpperBound(output, 1000L));
        inventory.add(output, 10);
        assertFalse(inventory.isItemBelowUpperBound(output, 1000L));
    }

    @Test
    void recipeFluidBoundsCheckLowerReserveAndUpperTargetInventoryAmounts() {
        AutomatedFacilityInventory inventory = new AutomatedFacilityInventory();
        inventory.addFluid(INPUT_KEY, 1000);
        inventory.addFluid(OUTPUT_KEY, 900);

        assertTrue(inventory.keepsFluidLowerBoundAfterConsume(INPUT_KEY, 200L, 800L));
        assertFalse(inventory.keepsFluidLowerBoundAfterConsume(INPUT_KEY, 201L, 800L));
        assertTrue(inventory.isFluidBelowUpperBound(OUTPUT_KEY, 1000L));
        inventory.addFluid(OUTPUT_KEY, 100);
        assertFalse(inventory.isFluidBelowUpperBound(OUTPUT_KEY, 1000L));
    }

    private static ItemStackWrapper resource() {
        return new ItemStackWrapper(new Item(), 0, null);
    }
}
