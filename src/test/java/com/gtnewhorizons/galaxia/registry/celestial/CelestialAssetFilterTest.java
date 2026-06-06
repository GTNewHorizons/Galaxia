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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialAssetFilterTest {

    private static AutomatedFacility facility;

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
    }

    @BeforeEach
    void clearFilters() {
        facility.getItemFilter()
            .clear();
        facility.getFluidFilter()
            .clear();
    }

    @Test
    void addFilterAndGetFilters() {
        ItemStackWrapper key = ItemStackWrapper.of(new ItemStack(Items.diamond, 1, 3));
        facility.addFilter(
            key.toItemStack()
                .getUnlocalizedName(),
            true);
        List<String> filters = facility.getItemFilter()
            .serialize();
        assertEquals(1, filters.size());
    }

    @Test
    void removeFilter() {
        ItemStackWrapper key = ItemStackWrapper.of(new ItemStack(Items.diamond, 1, 5));
        String name = key.toItemStack()
            .getUnlocalizedName();
        facility.addFilter(name, true);
        facility.removeFilter(name, true);
        assertTrue(
            facility.getItemFilter()
                .isEmpty());
    }

    @Test
    void testClearFilters() {
        ItemStackWrapper key = ItemStackWrapper.of(new ItemStack(Items.diamond));
        facility.addFilter(
            key.toItemStack()
                .getUnlocalizedName(),
            true);
        assertFalse(
            facility.getItemFilter()
                .isEmpty());
        facility.getItemFilter()
            .clear();
        assertTrue(
            facility.getItemFilter()
                .isEmpty());
    }

    @Test
    void setFiltersReplacesExisting() {
        ItemStackWrapper a = ItemStackWrapper.of(new ItemStack(Items.diamond));
        ItemStackWrapper b = ItemStackWrapper.of(new ItemStack(Items.stick));
        String aName = a.toItemStack()
            .getUnlocalizedName();
        String bName = b.toItemStack()
            .getUnlocalizedName();
        facility.addFilter(aName, true);
        facility.setFilters(List.of(bName), true);
        List<String> filters = facility.getItemFilter()
            .serialize();
        assertEquals(1, filters.size());
        assertEquals(bName, filters.get(0));
    }

    @Test
    void getItemFilterRespectsAddedItems() {
        ItemStack stack = new ItemStack(Items.diamond, 1, 7);
        ItemStackWrapper key = ItemStackWrapper.of(stack);
        String name = key.toItemStack()
            .getUnlocalizedName();
        facility.addFilter(name, true);
        assertTrue(
            facility.getItemFilter()
                .test(key));
        assertFalse(
            facility.getItemFilter()
                .test(ItemStackWrapper.of(new ItemStack(Items.stick))));
    }

    @Test
    void getItemFilterAcceptsAllWhenNoFilters() {
        assertTrue(
            facility.getItemFilter()
                .test(ItemStackWrapper.of(new ItemStack(Items.diamond))));
    }

    @Test
    void filtersSnapshotContainsSerializedKeys() {
        ItemStackWrapper key = ItemStackWrapper.of(new ItemStack(Items.diamond, 1, 10));
        facility.addFilter(
            key.toItemStack()
                .getUnlocalizedName(),
            true);
        var snapshot = facility.filtersSnapshot();
        assertFalse(snapshot.isEmpty());
        assertTrue(snapshot.containsKey(true));
    }

    @Test
    void fluidFilterAddAndGet() {
        FluidKey water = FluidKey.of(new FluidStack(FluidRegistry.WATER, 1));
        facility.addFilter(
            water.fluid()
                .getName(),
            false);
        assertEquals(
            1,
            facility.getFluidFilter()
                .serialize()
                .size());
        assertTrue(
            facility.getFluidFilter()
                .test(water));
        facility.getFluidFilter()
            .clear();
    }

    @Test
    void fluidFilterRespectsAddedFluids() {
        FluidKey water = FluidKey.of(new FluidStack(FluidRegistry.WATER, 1));
        FluidKey lava = FluidKey.of(new FluidStack(FluidRegistry.LAVA, 1));
        facility.addFilter(
            water.fluid()
                .getName(),
            false);
        assertTrue(
            facility.getFluidFilter()
                .test(water));
        assertFalse(
            facility.getFluidFilter()
                .test(lava));
        facility.getFluidFilter()
            .clear();
    }

    @Test
    void fluidFilterAcceptsAllWhenNoFilters() {
        assertTrue(
            facility.getFluidFilter()
                .test(FluidKey.of(new FluidStack(FluidRegistry.WATER, 1))));
    }
}
