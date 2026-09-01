package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBookOwner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeScheduleState;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ProductionModuleHelperTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void successfulExchangeAdvancesThePerModuleOrderSchedule() {
        AutomatedFacility facility = facility();
        RecipeBook book = orderBook(
            recipe(Items.diamond, Items.iron_ingot, 1),
            recipe(Items.gold_ingot, Items.coal, 1));
        ModuleInstance module = installBook(facility, book);
        ItemStackWrapper input = ItemStackWrapper.of(new ItemStack(Items.diamond));
        ItemStackWrapper output = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        facility.insert(input, 1L);
        RecipeScheduleState before = new RecipeScheduleState((byte) 0, (byte) 1);
        facility.restoreRecipeScheduleState(module, before);

        execute(facility, module, new Random(0));

        assertEquals(0L, facility.itemAmount(input));
        assertEquals(1L, facility.itemAmount(output));
        assertEquals(new RecipeScheduleState((byte) 1, (byte) 1), facility.recipeScheduleState(module));
    }

    @Test
    void failedExchangeDoesNotAdvanceTheSchedule() {
        AutomatedFacility facility = facility();
        ModuleInstance module = installBook(facility, orderBook(recipe(Items.diamond, Items.iron_ingot, 1)));
        RecipeScheduleState before = new RecipeScheduleState((byte) 0, (byte) 1);
        facility.restoreRecipeScheduleState(module, before);

        execute(facility, module, new Random(0));

        assertEquals(before, facility.recipeScheduleState(module));
    }

    @Test
    void combinedInputCostMustRemainAboveTheConfiguredLowerBound() {
        AutomatedFacility facility = facility();
        SavedRecipe recipe = savedRecipe(
            new ItemStack[] { new ItemStack(Items.diamond, 2), new ItemStack(Items.diamond, 4) },
            new ItemStack[] { new ItemStack(Items.iron_ingot) },
            0L);
        ModuleInstance module = installBook(facility, orderBook(recipe));
        ItemStackWrapper input = ItemStackWrapper.of(new ItemStack(Items.diamond));
        facility.insert(input, 105L);
        facility.setBound(input, 100L, true);
        RecipeScheduleState before = new RecipeScheduleState((byte) 0, (byte) 1);
        facility.restoreRecipeScheduleState(module, before);

        execute(facility, module, new Random(0));

        assertEquals(105L, facility.itemAmount(input));
        assertEquals(before, facility.recipeScheduleState(module));
    }

    @Test
    void requestAmountStopsProductionAtTheRequestedInventoryLevel() {
        AutomatedFacility facility = facility();
        ModuleInstance module = installBook(
            facility,
            orderBook(
                savedRecipe(
                    new ItemStack[] { new ItemStack(Items.diamond) },
                    new ItemStack[] { new ItemStack(Items.iron_ingot) },
                    5L)));
        ItemStackWrapper input = ItemStackWrapper.of(new ItemStack(Items.diamond));
        ItemStackWrapper output = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        facility.insert(input, 1L);
        facility.insert(output, 5L);
        RecipeScheduleState before = new RecipeScheduleState((byte) 0, (byte) 1);
        facility.restoreRecipeScheduleState(module, before);

        execute(facility, module, new Random(0));

        assertEquals(1L, facility.itemAmount(input));
        assertEquals(5L, facility.itemAmount(output));
        assertEquals(before, facility.recipeScheduleState(module));
    }

    @Test
    void rejectedOutputDoesNotConsumeInputOrAdvanceSchedule() {
        AutomatedFacility facility = facility();
        ModuleInstance module = installBook(facility, orderBook(recipe(Items.diamond, Items.iron_ingot, 1)));
        ItemStackWrapper input = ItemStackWrapper.of(new ItemStack(Items.diamond));
        facility.insert(input, 1L);
        facility.addFilter(new ItemStack(Items.gold_ingot).getUnlocalizedName(), true);
        RecipeScheduleState before = new RecipeScheduleState((byte) 0, (byte) 1);
        facility.restoreRecipeScheduleState(module, before);

        execute(facility, module, new Random(0));

        assertEquals(1L, facility.itemAmount(input));
        assertEquals(before, facility.recipeScheduleState(module));
    }

    private static void execute(AutomatedFacility facility, ModuleInstance module, Random random) {
        ProductionModuleHelper.execute(module, facility, random);
    }

    private static ModuleInstance installBook(AutomatedFacility facility, RecipeBook book) {
        FacilityModuleKind kind = FacilityModuleKind.MACERATOR;
        ModuleInstance module = FacilityModuleRegistry.create(
            new ModuleInstance.ID(new UUID(0L, 1L)),
            kind,
            StationTileCoord.of(0, 0),
            kind.defaultShape(),
            kind.defaultTier());
        facility.addModule(module);
        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.ReplaceRecipeBook(facility.assetId, new RecipeBookOwner.Private(module.id), book),
            FacilityCommand.Authority.NONE);
        assertSame(FacilityCommand.Result.CHANGED, result);
        return module;
    }

    private static RecipeBook orderBook(SavedRecipe... recipes) {
        return new RecipeBook(List.of(recipes), RecipeSchedulerMode.ORDER, NotDoablePolicy.SKIP);
    }

    private static SavedRecipe recipe(Item input, Item output, int outputSize) {
        return savedRecipe(
            new ItemStack[] { new ItemStack(input) },
            new ItemStack[] { new ItemStack(output, outputSize) },
            0L);
    }

    private static SavedRecipe savedRecipe(ItemStack[] inputs, ItemStack[] outputs, long requestAmount) {
        return new SavedRecipe(
            RecipeSnapshot.resolved((byte) 1, 0, inputs, outputs, null, null, 20, 30),
            true,
            requestAmount,
            (byte) 1,
            (byte) 1);
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }
}
