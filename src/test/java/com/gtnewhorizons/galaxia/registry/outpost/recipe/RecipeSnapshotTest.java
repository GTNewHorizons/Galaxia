package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.TestFMLRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

import sun.misc.Unsafe;

final class RecipeSnapshotTest {

    @BeforeAll
    static void init() {
        TestFMLRegistry.init();
    }

    @Test
    void snapshotRetainsFluidInputsAndOutputs() {
        FluidStack input = fluidStack("galaxia_test_input_fluid", 144);
        FluidStack output = fluidStack("galaxia_test_output_fluid", 72);

        RecipeSnapshot snapshot = new RecipeSnapshot(
            (byte) 1,
            5,
            123L,
            null,
            null,
            new FluidStack[] { input },
            new FluidStack[] { output },
            200,
            512);

        assertSame(input, snapshot.fluidInputs()[0]);
        assertSame(output, snapshot.fluidOutputs()[0]);
        assertEquals(200, snapshot.duration());
        assertEquals(512, snapshot.eut());
    }

    @Test
    void contentHashIncludesFluidIdentityAndAmount() {
        long base = RecipeSnapshot.computeContentHash(
            null,
            null,
            new FluidStack[] { fluidStack("galaxia_test_hash_input_fluid", 144) },
            null,
            100,
            512);
        long differentAmount = RecipeSnapshot.computeContentHash(
            null,
            null,
            new FluidStack[] { fluidStack("galaxia_test_hash_input_fluid", 288) },
            null,
            100,
            512);
        long differentFluid = RecipeSnapshot.computeContentHash(
            null,
            null,
            new FluidStack[] { fluidStack("galaxia_test_hash_other_fluid", 144) },
            null,
            100,
            512);

        assertNotEquals(base, differentAmount);
        assertNotEquals(base, differentFluid);
    }

    @Test
    void contentHashIncludesItemOutputChances() {
        Item outputItem = Items.diamond;
        ItemStack[] outputs = { new ItemStack(outputItem, 1, 0) };

        long base = RecipeSnapshot.computeContentHash(null, outputs, null, null, new int[] { 5000 }, 100, 512);
        long differentChance = RecipeSnapshot
            .computeContentHash(null, outputs, null, null, new int[] { 7500 }, 100, 512);

        assertNotEquals(base, differentChance);
    }

    @Test
    void contentHashIncludesFluidOutputChances() {
        FluidStack[] outputs = { fluidStack("galaxia_test_hash_chanced_fluid", 144) };

        long base = RecipeSnapshot.computeContentHash(null, null, null, outputs, null, new int[] { 5000 }, 100, 512);
        long differentChance = RecipeSnapshot
            .computeContentHash(null, null, null, outputs, null, new int[] { 7500 }, 100, 512);

        assertNotEquals(base, differentChance);
    }

    @Test
    void unresolvedSnapshotHasNoResolvedStacksOrFluids() {
        RecipeSnapshot snapshot = RecipeSnapshot.unresolved((byte) 2, 9, 456L);

        assertNull(snapshot.inputs());
        assertNull(snapshot.outputs());
        assertNull(snapshot.fluidInputs());
        assertNull(snapshot.fluidOutputs());
        assertNull(snapshot.outputChances());
        assertNull(snapshot.fluidOutputChances());
        assertEquals(0, snapshot.duration());
        assertEquals(0, snapshot.eut());
    }

    @Test
    void voltageTierDerivedFromEUt() {
        assertEquals(ModuleTier.NONE, RecipeVoltageTier.fromEUt(0));
        assertEquals(ModuleTier.HV, RecipeVoltageTier.fromEUt(512));
        assertEquals(ModuleTier.EV, RecipeVoltageTier.fromEUt(2048));
        assertEquals(ModuleTier.IV, RecipeVoltageTier.fromEUt(8192));
        assertEquals(ModuleTier.LuV, RecipeVoltageTier.fromEUt(32768));
    }

    private static FluidStack fluidStack(String fluidName, int amount) {
        try {
            FluidStack stack = (FluidStack) unsafe().allocateInstance(FluidStack.class);
            var fluidField = FluidStack.class.getDeclaredField("fluid");
            fluidField.setAccessible(true);
            fluidField.set(stack, new Fluid(fluidName));
            stack.amount = amount;
            return stack;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Unsafe unsafe() {
        try {
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
