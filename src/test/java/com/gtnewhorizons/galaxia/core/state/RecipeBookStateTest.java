package com.gtnewhorizons.galaxia.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class RecipeBookStateTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    @Test
    void completeBookRoundTripsWithoutLosingOrderModesOrRecipeMetadata() {
        RecipeBook expected = completeBook();

        assertEquals(expected, RecipeBookState.decode(RecipeBookState.encode(expected)));
    }

    @Test
    void malformedModesAndBooksOverTheDomainLimitAreRejected() {
        NBTTagCompound invalidMode = RecipeBookState.encode(RecipeBook.empty());
        invalidMode.setString("mode", "NOT_A_MODE");

        NBTTagCompound oversized = RecipeBookState.encode(RecipeBook.empty());
        NBTTagList recipes = new NBTTagList();
        for (int i = 0; i <= RecipeBook.MAX_RECIPES; i++) recipes.appendTag(new NBTTagCompound());
        oversized.setTag("recipes", recipes);

        assertThrows(IllegalStateException.class, () -> RecipeBookState.decode(invalidMode));
        assertThrows(IllegalStateException.class, () -> RecipeBookState.decode(oversized));
    }

    @Test
    void malformedResourceAmountAndChanceAreRejected() {
        NBTTagCompound invalidAmount = RecipeBookState.encode(completeBook());
        firstRecipeResource(invalidAmount, "itemInputs").setLong("amount", 0L);

        NBTTagCompound invalidChance = RecipeBookState.encode(completeBook());
        firstRecipeResource(invalidChance, "itemOutputs").setInteger("chance", 10_001);

        assertThrows(IllegalStateException.class, () -> RecipeBookState.decode(invalidAmount));
        assertThrows(IllegalStateException.class, () -> RecipeBookState.decode(invalidChance));
    }

    @Test
    void resourceAmountsAboveStackLimitsAreRejected() {
        RecipeSnapshot snapshot = RecipeSnapshot
            .resolved((byte) 1, 0, new ItemStack[] { new ItemStack(Items.diamond) }, null, null, null, 20, 30);
        RecipeBook book = new RecipeBook(
            List.of(new SavedRecipe(snapshot, true, 0L, (byte) 1, (byte) 1)),
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP);
        NBTTagCompound encoded = RecipeBookState.encode(book);
        long oversizedAmount = (long) Integer.MAX_VALUE + 1L;
        firstRecipeResource(encoded, "itemInputs").setLong("amount", oversizedAmount);
        encoded.getTagList("recipes", 10)
            .getCompoundTagAt(0)
            .setLong("hash", singleItemInputHash(oversizedAmount));

        assertThrows(IllegalStateException.class, () -> RecipeBookState.decode(encoded));
    }

    private static RecipeBook completeBook() {
        ItemStack input = new ItemStack(Items.diamond, 2, 3);
        NBTTagCompound itemTag = new NBTTagCompound();
        itemTag.setString("state", "item-metadata");
        input.setTagCompound(itemTag);

        FluidStack fluidOutput = new FluidStack(FluidRegistry.WATER, 1_000);
        NBTTagCompound fluidTag = new NBTTagCompound();
        fluidTag.setString("state", "fluid-metadata");
        fluidOutput.tag = fluidTag;

        RecipeSnapshot snapshot = RecipeSnapshot.resolved(
            (byte) 1,
            42,
            new ItemStack[] { input },
            new ItemStack[] { new ItemStack(Items.emerald, 1, 0) },
            new FluidStack[] { new FluidStack(FluidRegistry.LAVA, 144) },
            new FluidStack[] { fluidOutput },
            new int[] { 7_500 },
            new int[] { 5_000 },
            200,
            480);
        SavedRecipe first = new SavedRecipe(snapshot, true, 64L, (byte) 4, (byte) 3, "Diamond wash");
        SavedRecipe second = new SavedRecipe(snapshot, false, 0L, (byte) 1, (byte) 1, "Second");
        return new RecipeBook(List.of(first, second), RecipeSchedulerMode.ORDER, NotDoablePolicy.BACK_TO_BEGINNING);
    }

    private static NBTTagCompound firstRecipeResource(NBTTagCompound book, String resources) {
        return book.getTagList("recipes", 10)
            .getCompoundTagAt(0)
            .getTagList(resources, 10)
            .getCompoundTagAt(0);
    }

    private static long singleItemInputHash(long amount) {
        long hash = 1L;
        hash = hash * 31 + Item.getIdFromItem(Items.diamond);
        hash = hash * 31;
        hash = hash * 31 + amount;
        hash = hash * 31;
        hash = hash * 31 + 20;
        return hash * 31 + 30;
    }
}
