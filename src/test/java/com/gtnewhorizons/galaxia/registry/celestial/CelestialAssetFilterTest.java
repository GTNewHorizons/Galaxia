package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.TestFMLRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;

final class CelestialAssetFilterTest {

    private static AutomatedFacility facility;

    @BeforeAll
    static void initRegistries() {
        TestFMLRegistry.init();
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
        facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
    }

    @Test
    void addFilterAndGetFiltersFor() {
        ItemStack stack = new ItemStack(Items.diamond, 1, 3);
        facility.addFilter(0, stack);
        List<ItemStack> filters = facility.getFiltersFor(0);
        assertEquals(1, filters.size());
        assertEquals(
            3,
            filters.get(0)
                .getItemDamage());
        facility.clearFilters(0);
    }

    @Test
    void removeFilter() {
        ItemStack stack = new ItemStack(Items.diamond, 1, 5);
        facility.addFilter(0, stack);
        facility.removeFilter(0, stack);
        assertTrue(
            facility.getFiltersFor(0)
                .isEmpty());
    }

    @Test
    void clearFilters() {
        facility.addFilter(0, new ItemStack(Items.diamond));
        assertFalse(
            facility.getFiltersFor(0)
                .isEmpty());
        facility.clearFilters(0);
        assertTrue(
            facility.getFiltersFor(0)
                .isEmpty());
    }

    @Test
    void setFiltersReplacesExisting() {
        ItemStack a = new ItemStack(Items.diamond, 1, 1);
        ItemStack b = new ItemStack(Items.diamond, 1, 2);
        facility.addFilter(0, a);
        facility.setFilters(0, List.of(b));
        List<ItemStack> filters = facility.getFiltersFor(0);
        assertEquals(1, filters.size());
        assertEquals(
            2,
            filters.get(0)
                .getItemDamage());
        facility.clearFilters(0);
    }

    @Test
    void getItemFilterRespectsAddedItems() {
        ItemStack stack = new ItemStack(Items.diamond, 1, 7);
        ItemStackWrapper key = ItemStackWrapper.of(stack);
        facility.addFilter(0, stack);
        assertTrue(
            facility.getItemFilter(0)
                .test(key));
        assertFalse(
            facility.getItemFilter(0)
                .test(ItemStackWrapper.of(new ItemStack(Items.diamond, 1, 99))));
        facility.clearFilters(0);
    }

    @Test
    void getItemFilterAcceptsAllWhenNoFilters() {
        assertTrue(
            facility.getItemFilter(0)
                .test(ItemStackWrapper.of(new ItemStack(Items.diamond))));
    }

    @Test
    void filtersSnapshotContainsSerializedKeys() {
        ItemStack stack = new ItemStack(Items.diamond, 1, 10);
        facility.addFilter(0, stack);
        var snapshot = facility.filtersSnapshot();
        assertFalse(snapshot.isEmpty());
        assertTrue(
            snapshot.get(0)
                .stream()
                .anyMatch(k -> !k.startsWith("~:")));
        facility.clearFilters(0);
    }

    @Test
    void fluidFilterAddAndGet() {
        FluidKey water = FluidKey.of(new FluidStack(FluidRegistry.WATER, 1));
        facility.addFluidFilter(0, water);
        assertEquals(
            1,
            facility.getFluidFiltersFor(0)
                .size());
        assertTrue(
            facility.getFluidFilter(0)
                .test(water));
        facility.clearFluidFilters(0);
    }

    @Test
    void fluidFilterRespectsAddedFluids() {
        FluidKey water = FluidKey.of(new FluidStack(FluidRegistry.WATER, 1));
        FluidKey lava = FluidKey.of(new FluidStack(FluidRegistry.LAVA, 1));
        facility.addFluidFilter(0, water);
        assertTrue(
            facility.getFluidFilter(0)
                .test(water));
        assertFalse(
            facility.getFluidFilter(0)
                .test(lava));
        facility.clearFluidFilters(0);
    }

    @Test
    void fluidFilterAcceptsAllWhenNoFilters() {
        assertTrue(
            facility.getFluidFilter(0)
                .test(FluidKey.of(new FluidStack(FluidRegistry.WATER, 1))));
    }
}
