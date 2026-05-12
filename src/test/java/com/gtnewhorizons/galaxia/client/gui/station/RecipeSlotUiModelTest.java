package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;

import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

final class RecipeSlotUiModelTest {

    @Test
    void unresolvedRecipeFallsBackToRecipeIndex() {
        SavedRecipe slot = new SavedRecipe(RecipeSnapshot.unresolved((byte) 1, 7, 42L), true, 0L, (byte) 1, (byte) 1);

        assertEquals("Recipe #7", RecipeSlotUiModel.slotTitle(slot));
    }

    @Test
    void parseIntClampsToAllowedRange() {
        assertEquals(0, RecipeSlotUiModel.parseIntOrCurrent("-10", 5, 0, 10));
        assertEquals(10, RecipeSlotUiModel.parseIntOrCurrent("99", 5, 0, 10));
        assertEquals(5, RecipeSlotUiModel.parseIntOrCurrent("bad", 5, 0, 10));
    }

    @Test
    void nextModeCyclesSchedulerMode() {
        assertEquals(RecipeSchedulerMode.ORDER, RecipeSlotUiModel.nextMode(RecipeConfig.empty()));
    }

    @Test
    void fluidSlotAmountTextShowsLitersForPositiveAmounts() {
        assertEquals("1000L", RecipeSlotUiModel.fluidSlotAmountText(fluidStackWithAmount(1000)));
    }

    private static FluidStack fluidStackWithAmount(int amount) {
        try {
            FluidStack stack = (FluidStack) unsafe().allocateInstance(FluidStack.class);
            stack.amount = amount;
            return stack;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static sun.misc.Unsafe unsafe() throws ReflectiveOperationException {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }
}
