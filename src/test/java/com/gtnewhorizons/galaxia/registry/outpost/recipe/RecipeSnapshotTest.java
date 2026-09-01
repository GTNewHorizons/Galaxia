package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class RecipeSnapshotTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    @Test
    void snapshotResourcesProtectSourceAndProjectedFluidState() {
        FluidStack input = new FluidStack(FluidRegistry.WATER, 144);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("state", "original");
        input.tag = tag;
        RecipeSnapshot snapshot = RecipeSnapshot
            .resolved((byte) 1, 5, null, null, new FluidStack[] { input }, null, 200, 512);

        input.amount = 999;
        tag.setString("state", "changed");
        FluidStack firstRead = snapshot.fluidInputs()
            .get(0)
            .fluidStack();
        assertNotSame(input, firstRead);
        assertEquals(144, firstRead.amount);
        assertEquals("original", firstRead.tag.getString("state"));

        firstRead.amount = 998;
        firstRead.tag.setString("state", "changed again");
        FluidStack secondRead = snapshot.fluidInputs()
            .get(0)
            .fluidStack();
        assertEquals(144, secondRead.amount);
        assertEquals("original", secondRead.tag.getString("state"));
    }

    @Test
    void contentHashIncludesFluidIdentityAndAmount() {
        long base = resolvedFluidInput(FluidRegistry.WATER, 144).contentHash();
        long differentAmount = resolvedFluidInput(FluidRegistry.WATER, 288).contentHash();
        long differentFluid = resolvedFluidInput(FluidRegistry.LAVA, 144).contentHash();

        assertEquals(104_268_737_199_554L, base);
        assertNotEquals(base, differentAmount);
        assertNotEquals(base, differentFluid);
    }

    @Test
    void contentHashIncludesItemAndFluidOutputChances() {
        Item outputItem = new Item();
        ItemStack[] itemOutputs = { new ItemStack(outputItem, 1, 0) };
        FluidStack[] fluidOutputs = { new FluidStack(FluidRegistry.WATER, 144) };

        RecipeSnapshot base = RecipeSnapshot.resolved(
            (byte) 1,
            0,
            null,
            itemOutputs,
            null,
            fluidOutputs,
            new int[] { 5000 },
            new int[] { 5000 },
            100,
            512);
        RecipeSnapshot changed = RecipeSnapshot.resolved(
            (byte) 1,
            0,
            null,
            itemOutputs,
            null,
            fluidOutputs,
            new int[] { 7500 },
            new int[] { 7500 },
            100,
            512);

        assertNotEquals(base.contentHash(), changed.contentHash());
    }

    @Test
    void absentChanceRemainsDistinctFromExplicitGuaranteedChance() {
        ItemStack[] outputs = { new ItemStack(new Item(), 1, 0) };
        RecipeSnapshot absent = RecipeSnapshot.resolved((byte) 1, 0, null, outputs, null, null, 100, 512);
        RecipeSnapshot explicit = RecipeSnapshot
            .resolved((byte) 1, 0, null, outputs, null, null, new int[] { 10_000 }, 100, 512);

        assertFalse(
            absent.itemOutputs()
                .get(0)
                .hasChance());
        assertTrue(
            explicit.itemOutputs()
                .get(0)
                .hasChance());
        assertEquals(
            10_000,
            absent.itemOutputs()
                .get(0)
                .effectiveChance());
        assertNotEquals(absent.contentHash(), explicit.contentHash());
    }

    @Test
    void resourceChanceUsesGregTechTenThousandUnitBoundaries() {
        RecipeSnapshot.Resource absent = new RecipeSnapshot.Resource(new ItemStackWrapper(new Item(), 0, null), 1L);
        RecipeSnapshot.Resource impossible = new RecipeSnapshot.Resource(
            new ItemStackWrapper(new Item(), 0, null),
            1L,
            0);
        RecipeSnapshot.Resource guaranteed = new RecipeSnapshot.Resource(
            new ItemStackWrapper(new Item(), 0, null),
            1L,
            10_000);
        RecipeSnapshot.Resource half = new RecipeSnapshot.Resource(
            new ItemStackWrapper(new Item(), 0, null),
            1L,
            5_000);

        assertTrue(absent.shouldProduce(new FixedRandom(9_999)));
        assertFalse(impossible.shouldProduce(new FixedRandom(0)));
        assertTrue(guaranteed.shouldProduce(new FixedRandom(9_999)));
        assertTrue(half.shouldProduce(new FixedRandom(4_999)));
        assertFalse(half.shouldProduce(new FixedRandom(5_000)));
    }

    @Test
    void unresolvedSnapshotHasNoResolvedResources() {
        RecipeSnapshot snapshot = RecipeSnapshot.unresolved((byte) 2, 9, 456L);

        assertTrue(
            snapshot.itemInputs()
                .isEmpty());
        assertTrue(
            snapshot.itemOutputs()
                .isEmpty());
        assertTrue(
            snapshot.fluidInputs()
                .isEmpty());
        assertTrue(
            snapshot.fluidOutputs()
                .isEmpty());
        assertEquals(0, snapshot.duration());
        assertEquals(0, snapshot.eut());
    }

    private static RecipeSnapshot resolvedFluidInput(net.minecraftforge.fluids.Fluid fluid, int amount) {
        return RecipeSnapshot
            .resolved((byte) 1, 0, null, null, new FluidStack[] { new FluidStack(fluid, amount) }, null, 100, 512);
    }

    private static final class FixedRandom extends Random {

        private final int value;

        private FixedRandom(int value) {
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            assertEquals(10_000, bound);
            return value;
        }
    }
}
