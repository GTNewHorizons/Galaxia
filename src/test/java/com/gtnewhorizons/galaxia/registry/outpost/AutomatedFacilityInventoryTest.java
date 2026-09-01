package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AutomatedFacilityInventoryTest {

    private static FluidKey INPUT_KEY;
    private static FluidKey OUTPUT_KEY;

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        INPUT_KEY = new FluidKey(FluidRegistry.LAVA, null);
        OUTPUT_KEY = new FluidKey(FluidRegistry.WATER, null);
    }

    @Test
    void recipeBoundsCheckLowerReserveAndUpperTargetInventoryAmounts() {
        AutomatedFacility outpost = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PROXIMA_CENTAURI,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        ItemStackWrapper input = new ItemStackWrapper(Items.diamond, 0, null);
        ItemStackWrapper output = new ItemStackWrapper(Items.iron_ingot, 0, null);
        outpost.insert(input, 40);
        outpost.insert(output, 990);
        // Hits capacity limit
        assertEquals(960, outpost.itemAmount(output));
        outpost.extract(output, 470);
        outpost.setBound(input, 32L, true);
        outpost.setBound(output, 500L, false);

        assertTrue(outpost.isAboveLow(input, 8));
        assertFalse(outpost.isAboveLow(input, 9));
        assertTrue(outpost.isBelowUpper(input));
        outpost.insert(output, 10);
        assertFalse(outpost.isBelowUpper(output));
    }

    @Test
    void recipeFluidBoundsCheckLowerReserveAndUpperTargetInventoryAmounts() {
        AutomatedFacility outpost = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PROXIMA_CENTAURI,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        outpost.insert(INPUT_KEY, 1000);
        outpost.insert(OUTPUT_KEY, 900);
        outpost.setBound(INPUT_KEY, 800L, true);
        outpost.setBound(OUTPUT_KEY, 1000L, false);

        assertTrue(outpost.isAboveLow(INPUT_KEY, 200L));
        assertFalse(outpost.isAboveLow(INPUT_KEY, 201L));
        assertTrue(outpost.isBelowUpper(OUTPUT_KEY));
        outpost.insert(OUTPUT_KEY, 100);
        assertFalse(outpost.isBelowUpper(OUTPUT_KEY));
    }

    @Test
    void oneSidedChangesPreserveTheOtherAutomatedFacilityBound() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.stick));
        facility.setBound(item, 10L, 20L);
        InventoryBounds original = facility.getBound(item);

        facility.clearBound(item, true);

        assertEquals(new InventoryBounds(10L, 20L), original);
        assertFalse(facility.hasLowerBound(item));
        assertEquals(
            20L,
            facility.getBound(item)
                .upper());
        assertFalse(facility.clearBound(item, true));

        facility.setBound(item, 15L, true);
        assertEquals(new InventoryBounds(15L, 20L), facility.getBound(item));
        assertFalse(facility.trySetBound(item, 25L, true));
        assertEquals(new InventoryBounds(15L, 20L), facility.getBound(item));

        facility.clearBound(item, false);
        assertEquals(
            15L,
            facility.getBound(item)
                .low());
        assertFalse(facility.hasUpperBound(item));
        assertFalse(facility.clearBound(item, false));

        facility.clearBound(item, true);
        assertTrue(
            facility.getBound(item)
                .isInvalid());
    }
}
