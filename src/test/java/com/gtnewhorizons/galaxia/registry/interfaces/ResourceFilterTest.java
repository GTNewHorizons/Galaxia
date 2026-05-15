package com.gtnewhorizons.galaxia.registry.interfaces;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.TestFMLRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

final class ResourceFilterTest {

    @BeforeAll
    static void initRegistries() {
        TestFMLRegistry.init();
    }

    @Test
    void emptyFilterAcceptsEverything() {
        ResourceFilter<ItemStackWrapper> f = ResourceFilter.forItems();
        assertTrue(f.test(ItemStackWrapper.of(new ItemStack(Items.diamond))));
        assertTrue(f.isEmpty());
    }

    @Test
    void identityFilterMatchesEqualKeys() {
        ResourceFilter<ItemStackWrapper> f = ResourceFilter.forItems();
        ItemStackWrapper key = ItemStackWrapper.of(new ItemStack(Items.diamond, 1, 2));
        f.addIdentity(key);
        assertTrue(f.test(key));
        assertFalse(f.isEmpty());
    }

    @Test
    void identityFilterRejectsDifferentMeta() {
        ResourceFilter<ItemStackWrapper> f = ResourceFilter.forItems();
        f.addIdentity(ItemStackWrapper.of(new ItemStack(Items.diamond, 1, 2)));
        assertFalse(f.test(ItemStackWrapper.of(new ItemStack(Items.diamond, 1, 3))));
    }

    @Test
    void removeTakesOnlySpecifiedEntryOut() {
        ResourceFilter<ItemStackWrapper> f = ResourceFilter.forItems();
        ItemStackWrapper key1 = ItemStackWrapper.of(new ItemStack(Items.diamond, 1, 1));
        ItemStackWrapper key2 = ItemStackWrapper.of(new ItemStack(Items.diamond, 1, 2));
        f.addIdentity(key1);
        f.addIdentity(key2);
        f.remove(key1);
        assertFalse(f.test(key1));
        assertTrue(f.test(key2));
        assertFalse(f.isEmpty());
    }

    @Test
    void clearEmptiesFilter() {
        ResourceFilter<ItemStackWrapper> f = ResourceFilter.forItems();
        f.addIdentity(ItemStackWrapper.of(new ItemStack(Items.diamond)));
        f.addNameRegex(".*");
        f.clear();
        assertTrue(f.isEmpty());
        assertTrue(f.test(ItemStackWrapper.of(new ItemStack(Items.diamond))));
    }

    @Test
    void regexFilterMatchesByName() {
        ResourceFilter<FluidKey> f = ResourceFilter.forFluids();
        f.addNameRegex(".*water.*");
        assertTrue(f.test(FluidKey.of(new FluidStack(FluidRegistry.WATER, 1))));
        assertFalse(f.test(FluidKey.of(new FluidStack(FluidRegistry.LAVA, 1))));
    }

    @Test
    void mixedIdentityAndRegex() {
        ResourceFilter<FluidKey> f = ResourceFilter.forFluids();
        FluidKey water = FluidKey.of(new FluidStack(FluidRegistry.WATER, 1));
        f.addIdentity(water);
        f.addNameRegex(".*lava.*");
        assertTrue(f.test(water));
        assertTrue(f.test(FluidKey.of(new FluidStack(FluidRegistry.LAVA, 1))));
    }

    @Test
    void serializationRoundTripWithFluids() {
        ResourceFilter<FluidKey> f = ResourceFilter.forFluids();
        FluidKey water = FluidKey.of(new FluidStack(FluidRegistry.WATER, 1));
        f.addIdentity(water);
        f.addNameRegex(".*lava.*");

        ResourceFilter<FluidKey> restored = ResourceFilter.forFluids();
        restored.setAll(f.serialize());

        assertTrue(restored.test(water));
        assertTrue(restored.test(FluidKey.of(new FluidStack(FluidRegistry.LAVA, 1))));
    }
}
