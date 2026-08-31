package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

/** Product contract for immutable, completely validated recipe books. */
final class RecipeBookContractTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    @Test
    void constructionAndAccessDeeplyProtectRecipeContents() {
        ItemStack input = new ItemStack(new Item(), 2, 0);
        ItemStack output = new ItemStack(new Item(), 3, 1);
        FluidStack fluidInput = new FluidStack(FluidRegistry.WATER, 144);
        FluidStack fluidOutput = new FluidStack(FluidRegistry.LAVA, 288);
        ItemStack[] inputs = { input };
        ItemStack[] outputs = { output };
        FluidStack[] fluidInputs = { fluidInput };
        FluidStack[] fluidOutputs = { fluidOutput };
        int[] outputChances = { 7500 };
        int[] fluidOutputChances = { 5000 };
        SavedRecipe saved = new SavedRecipe(
            RecipeSnapshot.resolved(
                (byte) 1,
                7,
                inputs,
                outputs,
                fluidInputs,
                fluidOutputs,
                outputChances,
                fluidOutputChances,
                200,
                512),
            true,
            4L,
            (byte) 3,
            (byte) 2,
            "Macerate");
        List<SavedRecipe> callerRecipes = new ArrayList<>(List.of(saved));

        RecipeBook book = new RecipeBook(callerRecipes, RecipeSchedulerMode.ORDER, NotDoablePolicy.SKIP);

        callerRecipes.clear();
        input.stackSize = 99;
        output.stackSize = 98;
        fluidInput.amount = 97;
        fluidOutput.amount = 96;
        inputs[0] = new ItemStack(new Item(), 95, 0);
        outputs[0] = new ItemStack(new Item(), 94, 0);
        fluidInputs[0] = new FluidStack(FluidRegistry.WATER, 93);
        fluidOutputs[0] = new FluidStack(FluidRegistry.LAVA, 92);
        outputChances[0] = 1;
        fluidOutputChances[0] = 2;

        RecipeSnapshot firstRead = book.recipes()
            .get(0)
            .recipe();
        assertRecipeContents(firstRead);

        ItemStack[] readInputs = firstRead.inputs();
        ItemStack[] readOutputs = firstRead.outputs();
        FluidStack[] readFluidInputs = firstRead.fluidInputs();
        FluidStack[] readFluidOutputs = firstRead.fluidOutputs();
        int[] readOutputChances = firstRead.outputChances();
        int[] readFluidOutputChances = firstRead.fluidOutputChances();
        readInputs[0].stackSize = 88;
        readOutputs[0].stackSize = 87;
        readFluidInputs[0].amount = 86;
        readFluidOutputs[0].amount = 85;
        readInputs[0] = new ItemStack(new Item(), 84, 0);
        readOutputs[0] = new ItemStack(new Item(), 83, 0);
        readFluidInputs[0] = new FluidStack(FluidRegistry.WATER, 82);
        readFluidOutputs[0] = new FluidStack(FluidRegistry.LAVA, 81);
        readOutputChances[0] = 3;
        readFluidOutputChances[0] = 4;

        RecipeSnapshot secondRead = book.recipes()
            .get(0)
            .recipe();
        assertRecipeContents(secondRead);
        assertThrows(
            UnsupportedOperationException.class,
            () -> book.recipes()
                .clear());
    }

    @Test
    void constructionRejectsIncompleteOrInvalidBooks() {
        SavedRecipe valid = recipe(0, "Valid");
        RecipeSnapshot validSnapshot = valid.recipe();
        RecipeSnapshot mismatchedHash = new RecipeSnapshot(
            validSnapshot.recipeMapOrdinal(),
            validSnapshot.recipeIndex(),
            validSnapshot.contentHash() + 1,
            validSnapshot.inputs(),
            validSnapshot.outputs(),
            validSnapshot.fluidInputs(),
            validSnapshot.fluidOutputs(),
            validSnapshot.outputChances(),
            validSnapshot.fluidOutputChances(),
            validSnapshot.duration(),
            validSnapshot.eut());

        assertThrows(
            NullPointerException.class,
            () -> new RecipeBook(null, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP));
        assertThrows(
            NullPointerException.class,
            () -> new RecipeBook(Collections.singletonList(null), RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP));
        assertThrows(NullPointerException.class, () -> new RecipeBook(List.of(valid), null, NotDoablePolicy.SKIP));
        assertThrows(
            NullPointerException.class,
            () -> new RecipeBook(List.of(valid), RecipeSchedulerMode.PRIORITY, null));
        assertThrows(
            IllegalArgumentException.class,
            () -> new RecipeBook(
                Collections.nCopies(RecipeBook.MAX_RECIPES + 1, valid),
                RecipeSchedulerMode.PRIORITY,
                NotDoablePolicy.SKIP));
        assertThrows(
            IllegalArgumentException.class,
            () -> new RecipeBook(
                List.of(new SavedRecipe(RecipeSnapshot.unresolved((byte) 1, 0, 42L), true, 0L, (byte) 0, (byte) 1)),
                RecipeSchedulerMode.PRIORITY,
                NotDoablePolicy.SKIP));
        assertThrows(
            IllegalArgumentException.class,
            () -> new RecipeBook(
                List.of(new SavedRecipe(mismatchedHash, true, 0L, (byte) 0, (byte) 1)),
                RecipeSchedulerMode.PRIORITY,
                NotDoablePolicy.SKIP));
    }

    private static void assertRecipeContents(RecipeSnapshot snapshot) {
        assertEquals(2, snapshot.inputs()[0].stackSize);
        assertEquals(3, snapshot.outputs()[0].stackSize);
        assertEquals(144, snapshot.fluidInputs()[0].amount);
        assertEquals(288, snapshot.fluidOutputs()[0].amount);
        assertEquals(7500, snapshot.outputChances()[0]);
        assertEquals(5000, snapshot.fluidOutputChances()[0]);
    }

    private static SavedRecipe recipe(int index, String name) {
        RecipeSnapshot snapshot = RecipeSnapshot.resolved(
            (byte) 1,
            index,
            new ItemStack[] { new ItemStack(new Item(), 1, 0) },
            new ItemStack[] { new ItemStack(new Item(), index + 1, 0) },
            null,
            null,
            100,
            32);
        return new SavedRecipe(snapshot, true, 0L, (byte) 1, (byte) 1, name);
    }
}
