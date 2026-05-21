package com.gtnewhorizons.galaxia.registry.outpost.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.item.Item;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

final class UpkeepDemandTest {

    private static final ItemStackWrapper REDSTONE = new ItemStackWrapper(new Item(), 0, null);
    private static final ItemStackWrapper GOLD = new ItemStackWrapper(new Item(), 0, null);

    @Test
    void emptyBuilderReturnsSharedEmptyDemand() {
        assertSame(
            UpkeepDemand.EMPTY,
            UpkeepDemand.builder()
                .build());
    }

    @Test
    void builderMergesMatchingItemsAndFluids() {
        UpkeepDemand demand = UpkeepDemand.builder()
            .item(REDSTONE, 4)
            .item(REDSTONE, 6)
            .fluid("galaxia.test.fluid", 100)
            .fluid("galaxia.test.fluid", 250)
            .build();

        assertEquals(
            10L,
            demand.itemsPerMinute()
                .get(REDSTONE));
        assertEquals(
            350L,
            demand.fluidsPerMinute()
                .get("galaxia.test.fluid"));
    }

    @Test
    void plusAggregatesItemAndFluidDemands() {
        UpkeepDemand first = UpkeepDemand.builder()
            .item(REDSTONE, 4)
            .fluid("galaxia.test.fluid", 100)
            .build();
        UpkeepDemand second = UpkeepDemand.builder()
            .item(REDSTONE, 6)
            .item(GOLD, 2)
            .fluid("galaxia.test.fluid", 50)
            .build();

        UpkeepDemand result = first.plus(second);

        assertEquals(
            10L,
            result.itemsPerMinute()
                .get(REDSTONE));
        assertEquals(
            2L,
            result.itemsPerMinute()
                .get(GOLD));
        assertEquals(
            150L,
            result.fluidsPerMinute()
                .get("galaxia.test.fluid"));
    }

    @Test
    void percentMultiplierScalesUpkeepWithCeiling() {
        UpkeepDemand demand = UpkeepDemand.builder()
            .item(REDSTONE, 3)
            .fluid("galaxia.test.fluid", 101)
            .build();

        UpkeepDemand result = demand.multiplyPercent(80);

        assertEquals(
            3L,
            result.itemsPerMinute()
                .get(REDSTONE));
        assertEquals(
            81L,
            result.fluidsPerMinute()
                .get("galaxia.test.fluid"));
    }

    @Test
    void constructorRejectsInvalidFluidNames() {
        assertThrows(
            IllegalArgumentException.class,
            () -> UpkeepDemand.builder()
                .fluid(" ", 1)
                .build());
    }
}
