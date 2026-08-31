package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class RecipeSnapshotTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    @Test
    void snapshotDeeplyProtectsFluidInputsAndOutputs() {
        FluidStack input = new FluidStack(FluidRegistry.WATER, 144);
        FluidStack output = new FluidStack(FluidRegistry.LAVA, 72);

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

        input.amount = 999;
        output.amount = 998;

        FluidStack firstInputRead = snapshot.fluidInputs()[0];
        FluidStack firstOutputRead = snapshot.fluidOutputs()[0];
        assertNotSame(input, firstInputRead);
        assertNotSame(output, firstOutputRead);
        assertEquals(144, firstInputRead.amount);
        assertEquals(72, firstOutputRead.amount);
        firstInputRead.amount = 997;
        firstOutputRead.amount = 996;
        assertEquals(144, snapshot.fluidInputs()[0].amount);
        assertEquals(72, snapshot.fluidOutputs()[0].amount);
        assertEquals(200, snapshot.duration());
        assertEquals(512, snapshot.eut());
    }

    @Test
    void contentHashIncludesFluidIdentityAndAmount() {
        long base = RecipeSnapshot.computeContentHash(
            null,
            null,
            new FluidStack[] { new FluidStack(FluidRegistry.WATER, 144) },
            null,
            100,
            512);
        long differentAmount = RecipeSnapshot.computeContentHash(
            null,
            null,
            new FluidStack[] { new FluidStack(FluidRegistry.WATER, 288) },
            null,
            100,
            512);
        long differentFluid = RecipeSnapshot.computeContentHash(
            null,
            null,
            new FluidStack[] { new FluidStack(FluidRegistry.LAVA, 144) },
            null,
            100,
            512);

        assertNotEquals(base, differentAmount);
        assertNotEquals(base, differentFluid);
    }

    @Test
    void contentHashIncludesItemOutputChances() {
        Item outputItem = new Item();
        ItemStack[] outputs = { new ItemStack(outputItem, 1, 0) };

        long base = RecipeSnapshot.computeContentHash(null, outputs, null, null, new int[] { 5000 }, 100, 512);
        long differentChance = RecipeSnapshot
            .computeContentHash(null, outputs, null, null, new int[] { 7500 }, 100, 512);

        assertNotEquals(base, differentChance);
    }

    @Test
    void contentHashIncludesFluidOutputChances() {
        FluidStack[] outputs = { new FluidStack(FluidRegistry.WATER, 144) };

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

}
