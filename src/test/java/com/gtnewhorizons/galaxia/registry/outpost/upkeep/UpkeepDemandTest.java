package com.gtnewhorizons.galaxia.registry.outpost.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.item.Item;
import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

final class UpkeepDemandTest {

    private static final ItemStackWrapper REDSTONE = new ItemStackWrapper(new Item(), 0, null);
    private static final ItemStackWrapper GOLD = new ItemStackWrapper(new Item(), 0, null);
    private static final FluidKey COOLANT = new FluidKey(new Fluid("galaxia.test.fluid"), null);

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
            .fluid(COOLANT, 100)
            .fluid(COOLANT, 250)
            .build();

        assertEquals(
            UpkeepAmount.ofWhole(10L),
            demand.itemsPerMinute()
                .get(REDSTONE));
        assertEquals(
            UpkeepAmount.ofWhole(350L),
            demand.fluidsPerMinute()
                .get(COOLANT));
    }

    @Test
    void plusAggregatesItemAndFluidDemands() {
        UpkeepDemand first = UpkeepDemand.builder()
            .item(REDSTONE, 4)
            .fluid(COOLANT, 100)
            .build();
        UpkeepDemand second = UpkeepDemand.builder()
            .item(REDSTONE, 6)
            .item(GOLD, 2)
            .fluid(COOLANT, 50)
            .build();

        UpkeepDemand result = first.plus(second);

        assertEquals(
            UpkeepAmount.ofWhole(10L),
            result.itemsPerMinute()
                .get(REDSTONE));
        assertEquals(
            UpkeepAmount.ofWhole(2L),
            result.itemsPerMinute()
                .get(GOLD));
        assertEquals(
            UpkeepAmount.ofWhole(150L),
            result.fluidsPerMinute()
                .get(COOLANT));
    }

    @Test
    void percentMultiplierScalesUpkeepWithCeiling() {
        UpkeepDemand demand = UpkeepDemand.builder()
            .item(REDSTONE, 3)
            .fluid(COOLANT, 101)
            .build();

        UpkeepDemand result = demand.multiplyPercent(80);

        assertEquals(
            UpkeepAmount.parse("2.4"),
            result.itemsPerMinute()
                .get(REDSTONE));
        assertEquals(
            UpkeepAmount.parse("80.8"),
            result.fluidsPerMinute()
                .get(COOLANT));
    }

    @Test
    void constructorRejectsNullFluidKeys() {
        assertThrows(
            NullPointerException.class,
            () -> UpkeepDemand.builder()
                .fluid((FluidKey) null, 1)
                .build());
    }
}
