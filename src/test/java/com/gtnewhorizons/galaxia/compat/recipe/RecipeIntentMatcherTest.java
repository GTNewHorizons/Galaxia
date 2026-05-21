package com.gtnewhorizons.galaxia.compat.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.TestFMLRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

import gregtech.api.util.GTRecipe;
import sun.misc.Unsafe;

final class RecipeIntentMatcherTest {

    @BeforeAll
    static void initRegistries() {
        TestFMLRegistry.init();
    }

    @Test
    void singleFluidInputMatchCreatesResolvedSnapshot() throws Exception {
        GTRecipe steamToWater = recipe(
            null,
            null,
            fluids("galaxia.intent.steam", 144),
            fluids("galaxia.intent.water", 1000),
            320,
            480);
        GTRecipe lavaToStone = recipe(
            null,
            null,
            fluids("galaxia.intent.lava", 144),
            fluids("galaxia.intent.stone", 1000),
            200,
            120);

        RecipeIntentMatcher.Result result = RecipeIntentMatcher.match(
            GTRecipeMapId.DISTILLERY,
            new GTRecipe[] { steamToWater, lavaToStone },
            null,
            null,
            fluids("galaxia.intent.steam", 1),
            null);

        assertEquals(RecipeIntentMatcher.Status.SINGLE_MATCH, result.status());
        assertEquals(1, result.matchCount());
        assertEquals(0, result.recipeIndex());
        RecipeSnapshot snapshot = result.snapshot();
        assertEquals(GTRecipeMapId.DISTILLERY.ordinal(), Byte.toUnsignedInt(snapshot.recipeMapOrdinal()));
        assertEquals(320, snapshot.duration());
        assertEquals(480, snapshot.eut());
        assertEquals("galaxia.intent.water", fluidName(snapshot.fluidOutputs()[0]));
    }

    @Test
    void multipleMatchesRequireMoreHardSlots() throws Exception {
        GTRecipe water = recipe(
            null,
            null,
            fluids("galaxia.intent.steam", 144),
            fluids("galaxia.intent.water", 1000),
            320,
            480);
        GTRecipe distilled = recipe(
            null,
            null,
            fluids("galaxia.intent.steam", 144),
            fluids("galaxia.intent.distilled", 1000),
            320,
            480);

        RecipeIntentMatcher.Result result = RecipeIntentMatcher.match(
            GTRecipeMapId.DISTILLERY,
            new GTRecipe[] { water, distilled },
            null,
            null,
            fluids("galaxia.intent.steam", 1),
            null);

        assertEquals(RecipeIntentMatcher.Status.MULTIPLE_MATCHES, result.status());
        assertEquals(2, result.matchCount());
        assertNull(result.snapshot());
    }

    @Test
    void outputSideHardSlotCanDisambiguateMatch() throws Exception {
        GTRecipe water = recipe(
            null,
            null,
            fluids("galaxia.intent.steam", 144),
            fluids("galaxia.intent.water", 1000),
            320,
            480);
        GTRecipe distilled = recipe(
            null,
            null,
            fluids("galaxia.intent.steam", 144),
            fluids("galaxia.intent.distilled", 1000),
            320,
            480);

        RecipeIntentMatcher.Result result = RecipeIntentMatcher.match(
            GTRecipeMapId.DISTILLERY,
            new GTRecipe[] { water, distilled },
            null,
            null,
            fluids("galaxia.intent.steam", 1),
            fluids("galaxia.intent.distilled", 1));

        assertEquals(RecipeIntentMatcher.Status.SINGLE_MATCH, result.status());
        assertEquals(1, result.recipeIndex());
    }

    @Test
    void singleItemOutputMatchCopiesOutputChancesIntoSnapshot() throws Exception {
        Item outputItem = Items.diamond;
        ItemStack[] outputs = { new ItemStack(outputItem, 1, 0) };
        GTRecipe recipe = recipe(null, outputs, null, null, new int[] { 5000 }, 320, 480);

        RecipeIntentMatcher.Result result = RecipeIntentMatcher
            .match(GTRecipeMapId.DISTILLERY, new GTRecipe[] { recipe }, null, outputs, null, null);

        assertEquals(RecipeIntentMatcher.Status.SINGLE_MATCH, result.status());
        assertEquals(
            5000,
            result.snapshot()
                .outputChances()[0]);
    }

    @Test
    void noHardSlotsAreReportedSeparatelyFromNoMatch() {
        RecipeIntentMatcher.Result result = RecipeIntentMatcher
            .match(GTRecipeMapId.DISTILLERY, new GTRecipe[0], null, null, null, null);

        assertEquals(RecipeIntentMatcher.Status.NO_INPUT, result.status());
        assertEquals(0, result.matchCount());
        assertNull(result.snapshot());
    }

    @Test
    void noRecipeFoundWhenHardSlotsDoNotMatch() throws Exception {
        GTRecipe water = recipe(
            null,
            null,
            fluids("galaxia.intent.steam", 144),
            fluids("galaxia.intent.water", 1000),
            320,
            480);

        RecipeIntentMatcher.Result result = RecipeIntentMatcher.match(
            GTRecipeMapId.DISTILLERY,
            new GTRecipe[] { water },
            null,
            null,
            fluids("galaxia.intent.lava", 1),
            null);

        assertEquals(RecipeIntentMatcher.Status.NO_MATCH, result.status());
        assertEquals(0, result.matchCount());
        assertNull(result.snapshot());
    }

    private static GTRecipe recipe(ItemStack[] itemInputs, ItemStack[] itemOutputs, FluidStack[] fluidInputs,
        FluidStack[] fluidOutputs, int duration, int eut) throws Exception {
        return recipe(itemInputs, itemOutputs, fluidInputs, fluidOutputs, null, duration, eut);
    }

    private static GTRecipe recipe(ItemStack[] itemInputs, ItemStack[] itemOutputs, FluidStack[] fluidInputs,
        FluidStack[] fluidOutputs, int[] outputChances, int duration, int eut) throws Exception {
        Unsafe unsafe = unsafe();
        GTRecipe recipe = (GTRecipe) unsafe.allocateInstance(GTRecipe.class);
        set(recipe, "mInputs", itemInputs);
        set(recipe, "mOutputs", itemOutputs);
        set(recipe, "mOutputChances", outputChances);
        set(recipe, "mFluidInputs", fluidInputs);
        set(recipe, "mFluidOutputs", fluidOutputs);
        set(recipe, "mDuration", duration);
        set(recipe, "mEUt", eut);
        set(recipe, "mHidden", false);
        set(recipe, "mFakeRecipe", false);
        return recipe;
    }

    private static FluidStack[] fluids(String fluidName, int amount) throws Exception {
        return new FluidStack[] { fluidStack(fluidName, amount) };
    }

    private static FluidStack fluidStack(String fluidName, int amount) throws Exception {
        Fluid fluid = new Fluid(fluidName);
        FluidStack stack = (FluidStack) unsafe().allocateInstance(FluidStack.class);
        Field fluidField = FluidStack.class.getDeclaredField("fluid");
        fluidField.setAccessible(true);
        fluidField.set(stack, fluid);
        stack.amount = amount;
        return stack;
    }

    private static String fluidName(FluidStack stack) throws Exception {
        try {
            return stack.getFluid()
                .getName();
        } catch (RuntimeException e) {
            Field fluidField = FluidStack.class.getDeclaredField("fluid");
            fluidField.setAccessible(true);
            Fluid fluid = (Fluid) fluidField.get(stack);
            return fluid != null ? fluid.getName() : null;
        }
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass()
            .getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Unsafe unsafe() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }
}
