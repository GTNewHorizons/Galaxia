package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import java.util.Arrays;
import java.util.Objects;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * Self-contained recipe data snapshot. Created by the picker GUI when
 * the player selects a recipe. The execution pipeline reads directly
 * from this record — zero GT5 imports.
 *
 * <p>
 * {@link #contentHash} enables validation on server restart:
 * if the hash changed, the recipe was modified by a mod update.
 */
public record RecipeSnapshot(byte recipeMapOrdinal, int recipeIndex, long contentHash, ItemStack[] inputs,
    ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int[] outputChances,
    int[] fluidOutputChances, int duration, int eut) {

    public RecipeSnapshot {
        if (duration < 0) duration = 0;
        if (eut < 0) eut = 0;
        inputs = copyItems(inputs);
        outputs = copyItems(outputs);
        fluidInputs = copyFluids(fluidInputs);
        fluidOutputs = copyFluids(fluidOutputs);
        outputChances = copyInts(outputChances);
        fluidOutputChances = copyInts(fluidOutputChances);
    }

    public RecipeSnapshot(byte recipeMapOrdinal, int recipeIndex, long contentHash, ItemStack[] inputs,
        ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int duration, int eut) {
        this(
            recipeMapOrdinal,
            recipeIndex,
            contentHash,
            inputs,
            outputs,
            fluidInputs,
            fluidOutputs,
            null,
            null,
            duration,
            eut);
    }

    public RecipeSnapshot(byte recipeMapOrdinal, int recipeIndex, long contentHash, ItemStack[] inputs,
        ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int[] outputChances, int duration,
        int eut) {
        this(
            recipeMapOrdinal,
            recipeIndex,
            contentHash,
            inputs,
            outputs,
            fluidInputs,
            fluidOutputs,
            outputChances,
            null,
            duration,
            eut);
    }

    public RecipeSnapshot(byte recipeMapOrdinal, int recipeIndex, long contentHash, ItemStack[] inputs,
        ItemStack[] outputs, int duration, int eut) {
        this(recipeMapOrdinal, recipeIndex, contentHash, inputs, outputs, null, null, null, null, duration, eut);
    }

    /** Creates an identity-only snapshot. Recipe books reject it until server content resolution completes. */
    public static RecipeSnapshot unresolved(byte recipeMapOrdinal, int recipeIndex, long contentHash) {
        return new RecipeSnapshot(recipeMapOrdinal, recipeIndex, contentHash, null, null, null, null, null, null, 0, 0);
    }

    public static RecipeSnapshot resolved(byte recipeMapOrdinal, int recipeIndex, ItemStack[] inputs,
        ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int duration, int eut) {
        return resolved(recipeMapOrdinal, recipeIndex, inputs, outputs, fluidInputs, fluidOutputs, null, duration, eut);
    }

    public static RecipeSnapshot resolved(byte recipeMapOrdinal, int recipeIndex, ItemStack[] inputs,
        ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int[] outputChances, int duration,
        int eut) {
        return resolved(
            recipeMapOrdinal,
            recipeIndex,
            inputs,
            outputs,
            fluidInputs,
            fluidOutputs,
            outputChances,
            null,
            duration,
            eut);
    }

    public static RecipeSnapshot resolved(byte recipeMapOrdinal, int recipeIndex, ItemStack[] inputs,
        ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int[] outputChances,
        int[] fluidOutputChances, int duration, int eut) {
        return new RecipeSnapshot(
            recipeMapOrdinal,
            recipeIndex,
            computeContentHash(
                inputs,
                outputs,
                fluidInputs,
                fluidOutputs,
                outputChances,
                fluidOutputChances,
                duration,
                eut),
            inputs,
            outputs,
            fluidInputs,
            fluidOutputs,
            outputChances,
            fluidOutputChances,
            duration,
            eut);
    }

    public static long computeContentHash(ItemStack[] inputs, ItemStack[] outputs, FluidStack[] fluidInputs,
        FluidStack[] fluidOutputs, int duration, int eut) {
        return computeContentHash(inputs, outputs, fluidInputs, fluidOutputs, null, duration, eut);
    }

    public static long computeContentHash(ItemStack[] inputs, ItemStack[] outputs, FluidStack[] fluidInputs,
        FluidStack[] fluidOutputs, int[] outputChances, int duration, int eut) {
        return computeContentHash(inputs, outputs, fluidInputs, fluidOutputs, outputChances, null, duration, eut);
    }

    public static long computeContentHash(ItemStack[] inputs, ItemStack[] outputs, FluidStack[] fluidInputs,
        FluidStack[] fluidOutputs, int[] outputChances, int[] fluidOutputChances, int duration, int eut) {
        long hash = 1L;
        hash = hashItems(hash, inputs);
        hash = hashItems(hash, outputs);
        hash = hashOutputChances(hash, outputChances);
        hash = hashFluids(hash, fluidInputs);
        hash = hashFluids(hash, fluidOutputs);
        hash = hashOutputChances(hash, fluidOutputChances);
        hash = hash * 31 + duration;
        hash = hash * 31 + eut;
        return hash;
    }

    public static long computeContentHash(ItemStack[] inputs, ItemStack[] outputs, int duration, int eut) {
        return computeContentHash(inputs, outputs, null, null, duration, eut);
    }

    void validateForBook() {
        if (Byte.toUnsignedInt(recipeMapOrdinal) == 0) {
            throw new IllegalArgumentException("Recipe map ordinal must identify a supported map");
        }
        if (recipeIndex < 0) throw new IllegalArgumentException("Recipe index must be non-negative");
        if (duration <= 0) throw new IllegalArgumentException("Recipe duration must be positive");
        validateItems(inputs, "inputs");
        validateItems(outputs, "outputs");
        validateFluids(fluidInputs, "fluidInputs");
        validateFluids(fluidOutputs, "fluidOutputs");
        if (!hasContent(inputs) && !hasContent(outputs) && !hasContent(fluidInputs) && !hasContent(fluidOutputs)) {
            throw new IllegalArgumentException("Recipe snapshot has no resolved content");
        }
        validateChances(outputChances, outputs == null ? 0 : outputs.length, "outputChances");
        validateChances(fluidOutputChances, fluidOutputs == null ? 0 : fluidOutputs.length, "fluidOutputChances");
        long expectedHash = computeContentHash(
            inputs,
            outputs,
            fluidInputs,
            fluidOutputs,
            outputChances,
            fluidOutputChances,
            duration,
            eut);
        if (contentHash != expectedHash) {
            throw new IllegalArgumentException("Recipe snapshot content hash does not match resolved content");
        }
    }

    @Override
    public ItemStack[] inputs() {
        return copyItems(inputs);
    }

    @Override
    public ItemStack[] outputs() {
        return copyItems(outputs);
    }

    @Override
    public FluidStack[] fluidInputs() {
        return copyFluids(fluidInputs);
    }

    @Override
    public FluidStack[] fluidOutputs() {
        return copyFluids(fluidOutputs);
    }

    @Override
    public int[] outputChances() {
        return copyInts(outputChances);
    }

    @Override
    public int[] fluidOutputChances() {
        return copyInts(fluidOutputChances);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeSnapshot snapshot)) return false;
        return recipeMapOrdinal == snapshot.recipeMapOrdinal && recipeIndex == snapshot.recipeIndex
            && contentHash == snapshot.contentHash
            && duration == snapshot.duration
            && eut == snapshot.eut
            && itemArraysEqual(inputs, snapshot.inputs)
            && itemArraysEqual(outputs, snapshot.outputs)
            && fluidArraysEqual(fluidInputs, snapshot.fluidInputs)
            && fluidArraysEqual(fluidOutputs, snapshot.fluidOutputs)
            && Arrays.equals(outputChances, snapshot.outputChances)
            && Arrays.equals(fluidOutputChances, snapshot.fluidOutputChances);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(recipeMapOrdinal, recipeIndex, contentHash, duration, eut);
        result = 31 * result + itemArrayHash(inputs);
        result = 31 * result + itemArrayHash(outputs);
        result = 31 * result + fluidArrayHash(fluidInputs);
        result = 31 * result + fluidArrayHash(fluidOutputs);
        result = 31 * result + Arrays.hashCode(outputChances);
        result = 31 * result + Arrays.hashCode(fluidOutputChances);
        return result;
    }

    private static ItemStack[] copyItems(ItemStack[] stacks) {
        if (stacks == null) return null;
        ItemStack[] copy = new ItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) copy[i] = stacks[i] == null ? null : stacks[i].copy();
        return copy;
    }

    private static FluidStack[] copyFluids(FluidStack[] stacks) {
        if (stacks == null) return null;
        FluidStack[] copy = new FluidStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) copy[i] = stacks[i] == null ? null : stacks[i].copy();
        return copy;
    }

    private static int[] copyInts(int[] values) {
        return values == null ? null : Arrays.copyOf(values, values.length);
    }

    private static void validateItems(ItemStack[] stacks, String field) {
        if (stacks == null) return;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
                throw new IllegalArgumentException("Recipe " + field + " contains an invalid item stack");
            }
        }
    }

    private static void validateFluids(FluidStack[] stacks, String field) {
        if (stacks == null) return;
        for (FluidStack stack : stacks) {
            if (stack == null || stack.getFluid() == null || stack.amount <= 0) {
                throw new IllegalArgumentException("Recipe " + field + " contains an invalid fluid stack");
            }
        }
    }

    private static void validateChances(int[] chances, int outputCount, String field) {
        if (chances == null) return;
        if (chances.length != outputCount) {
            throw new IllegalArgumentException("Recipe " + field + " count does not match outputs");
        }
        for (int chance : chances) {
            if (chance < 0 || chance > 10_000) {
                throw new IllegalArgumentException("Recipe " + field + " contains an invalid chance");
            }
        }
    }

    private static boolean hasContent(Object[] values) {
        return values != null && values.length > 0;
    }

    private static boolean itemArraysEqual(ItemStack[] first, ItemStack[] second) {
        if (first == second) return true;
        if (first == null || second == null || first.length != second.length) return false;
        for (int i = 0; i < first.length; i++) {
            if (!itemEquals(first[i], second[i])) return false;
        }
        return true;
    }

    private static boolean itemEquals(ItemStack first, ItemStack second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        return first.getItem() == second.getItem() && first.getItemDamage() == second.getItemDamage()
            && first.stackSize == second.stackSize
            && Objects.equals(first.getTagCompound(), second.getTagCompound());
    }

    private static int itemArrayHash(ItemStack[] stacks) {
        if (stacks == null) return 0;
        int result = 1;
        for (ItemStack stack : stacks) {
            int stackHash = stack == null ? 0
                : Objects.hash(stack.getItem(), stack.getItemDamage(), stack.stackSize, stack.getTagCompound());
            result = 31 * result + stackHash;
        }
        return result;
    }

    private static boolean fluidArraysEqual(FluidStack[] first, FluidStack[] second) {
        if (first == second) return true;
        if (first == null || second == null || first.length != second.length) return false;
        for (int i = 0; i < first.length; i++) {
            if (!fluidEquals(first[i], second[i])) return false;
        }
        return true;
    }

    private static boolean fluidEquals(FluidStack first, FluidStack second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        return first.getFluidID() == second.getFluidID() && first.amount == second.amount
            && Objects.equals(first.tag, second.tag);
    }

    private static int fluidArrayHash(FluidStack[] stacks) {
        if (stacks == null) return 0;
        int result = 1;
        for (FluidStack stack : stacks) {
            int stackHash = stack == null ? 0 : Objects.hash(stack.getFluidID(), stack.amount, stack.tag);
            result = 31 * result + stackHash;
        }
        return result;
    }

    private static long hashItems(long hash, ItemStack[] stacks) {
        if (stacks == null) return hash;
        for (ItemStack stack : stacks) {
            if (stack == null) continue;
            hash = hash * 31 + Item.getIdFromItem(stack.getItem());
            hash = hash * 31 + stack.getItemDamage();
            hash = hash * 31 + stack.stackSize;
            hash = hash * 31 + Objects.hashCode(stack.getTagCompound());
        }
        return hash;
    }

    private static long hashOutputChances(long hash, int[] chances) {
        if (chances == null) return hash;
        for (int chance : chances) {
            hash = hash * 31 + chance;
        }
        return hash;
    }

    private static long hashFluids(long hash, FluidStack[] fluids) {
        if (fluids == null) return hash;
        for (FluidStack fluid : fluids) {
            if (fluid == null) continue;
            Fluid fluidType = fluid.getFluid();
            hash = hash * 31 + (fluidType != null ? fluidType.getName()
                .hashCode() : fluid.getFluidID());
            hash = hash * 31 + fluid.amount;
            hash = hash * 31 + Objects.hashCode(fluid.tag);
        }
        return hash;
    }

}
