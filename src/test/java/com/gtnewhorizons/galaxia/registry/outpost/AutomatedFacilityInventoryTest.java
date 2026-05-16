package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.init.Items;
import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.TestFMLRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;

final class AutomatedFacilityInventoryTest {

    private static FluidKey INPUT_KEY;
    private static FluidKey OUTPUT_KEY;

    @BeforeAll
    static void initRegistries() {
        TestFMLRegistry.init();
        INPUT_KEY = new FluidKey(FluidRegistry.LAVA, null);
        OUTPUT_KEY = new FluidKey(FluidRegistry.WATER, null);
        CelestialRegistry.freezeAndBake();
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
        outpost.updateItems(input, 40);
        outpost.updateItems(output, 990);
        // Hits capacity limit
        assertEquals(960, outpost.getItemAmount(output));
        outpost.updateItems(output, -470);
        outpost.setBound(input, 32L, true);
        outpost.setBound(output, 500L, false);

        assertTrue(outpost.isAboveLow(input, 8));
        assertFalse(outpost.isAboveLow(input, 9));
        assertTrue(outpost.isBelowUpper(input));
        outpost.updateItems(output, 10);
        assertFalse(outpost.isBelowUpper(output));
    }

    @Test
    void recipeFluidBoundsCheckLowerReserveAndUpperTargetInventoryAmounts() {
        AutomatedFacility outpost = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PROXIMA_CENTAURI,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        outpost.updateFluids(INPUT_KEY, 1000);
        outpost.updateFluids(OUTPUT_KEY, 900);
        outpost.setBound(INPUT_KEY, 800L, true);
        outpost.setBound(OUTPUT_KEY, 1000L, false);

        assertTrue(outpost.isAboveLow(INPUT_KEY, 200L));
        assertFalse(outpost.isAboveLow(INPUT_KEY, 201L));
        assertTrue(outpost.isBelowUpper(OUTPUT_KEY));
        outpost.updateFluids(OUTPUT_KEY, 100);
        assertFalse(outpost.isBelowUpper(OUTPUT_KEY));
    }
}
