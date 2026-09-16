package com.gtnewhorizons.galaxia.registry.outpost.module;

import static com.gtnewhorizons.galaxia.registry.outpost.FacilityTestFixtures.addModule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
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
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
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
        RecipeBook.ScheduleState before = new RecipeBook.ScheduleState((byte) 0, (byte) 1);
        module.restoreRecipeScheduleState(before);

        execute(facility, module);

        assertEquals(0L, facility.itemAmount(input));
        assertEquals(1L, facility.itemAmount(output));
        assertEquals(new RecipeBook.ScheduleState((byte) 1, (byte) 1), module.recipeScheduleState());
    }

    @Test
    void failedExchangeDoesNotAdvanceTheSchedule() {
        AutomatedFacility facility = facility();
        ModuleInstance module = installBook(facility, orderBook(recipe(Items.diamond, Items.iron_ingot, 1)));
        RecipeBook.ScheduleState before = new RecipeBook.ScheduleState((byte) 0, (byte) 1);
        module.restoreRecipeScheduleState(before);

        execute(facility, module);

        assertEquals(before, module.recipeScheduleState());
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
        facility.applyCommand(
            new FacilityCommand.SetInventoryBound(facility.assetId, BoundKind.ITEM_LOWER, input, 100L),
            FacilityCommand.Authority.NONE);
        RecipeBook.ScheduleState before = new RecipeBook.ScheduleState((byte) 0, (byte) 1);
        module.restoreRecipeScheduleState(before);

        execute(facility, module);

        assertEquals(105L, facility.itemAmount(input));
        assertEquals(before, module.recipeScheduleState());
    }

    @Test
    void inputCostMustRemainAboveTheEffectiveLowerBoundWithoutAManualBound() {
        AutomatedFacility facility = facility();
        ModuleInstance module = installBook(facility, orderBook(recipe(Items.diamond, Items.iron_ingot, 1)));
        ItemStackWrapper input = ItemStackWrapper.of(new ItemStack(Items.diamond));
        facility.insert(input, 5L);
        facility.applyCommand(
            new FacilityCommand.PutLogisticsConfig(
                facility.assetId,
                input,
                new LogisticsResourceConfig(5, 1, false, false),
                LogisticsConfigAccessMode.FULL),
            FacilityCommand.Authority.NONE);
        RecipeBook.ScheduleState before = new RecipeBook.ScheduleState((byte) 0, (byte) 1);
        module.restoreRecipeScheduleState(before);

        execute(facility, module);

        assertEquals(5L, facility.itemAmount(input));
        assertEquals(before, module.recipeScheduleState());
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
        RecipeBook.ScheduleState before = new RecipeBook.ScheduleState((byte) 0, (byte) 1);
        module.restoreRecipeScheduleState(before);

        execute(facility, module);

        assertEquals(1L, facility.itemAmount(input));
        assertEquals(5L, facility.itemAmount(output));
        assertEquals(before, module.recipeScheduleState());
    }

    @Test
    void rejectedOutputDoesNotConsumeInputOrAdvanceSchedule() {
        AutomatedFacility facility = facility();
        ModuleInstance module = installBook(facility, orderBook(recipe(Items.diamond, Items.iron_ingot, 1)));
        ItemStackWrapper input = ItemStackWrapper.of(new ItemStack(Items.diamond));
        facility.insert(input, 1L);
        facility.addFilter(new ItemStack(Items.gold_ingot).getUnlocalizedName(), true);
        RecipeBook.ScheduleState before = new RecipeBook.ScheduleState((byte) 0, (byte) 1);
        module.restoreRecipeScheduleState(before);

        execute(facility, module);

        assertEquals(1L, facility.itemAmount(input));
        assertEquals(before, module.recipeScheduleState());
    }

    private static void execute(AutomatedFacility facility, ModuleInstance module) {
        module.component()
            .runCycle(module, facility);
    }

    // Product contract: blocked production changes the ORDER cursor according to the chosen policy, not inventory.
    @Test
    void blockedOrderFollowsTheConfiguredPolicyWithoutConsumingMaterials() {
        for (NotDoablePolicy policy : NotDoablePolicy.values()) {
            AutomatedFacility facility = facility();
            RecipeBook book = new RecipeBook(
                List.of(
                    recipe(Items.gold_ingot, Items.coal, 1),
                    recipe(Items.diamond, Items.iron_ingot, 1),
                    recipe(Items.stick, Items.feather, 1)),
                RecipeSchedulerMode.ORDER,
                policy);
            ModuleInstance module = installBook(facility, book);
            facility.insert(ItemStackWrapper.of(new ItemStack(Items.gold_ingot)), 1L);
            facility.insert(ItemStackWrapper.of(new ItemStack(Items.stick)), 1L);
            var inventoryBefore = facility.inventorySnapshot();
            module.restoreRecipeScheduleState(new RecipeBook.ScheduleState((byte) 1, (byte) 3));

            execute(facility, module);

            int expected = policy == NotDoablePolicy.SKIP ? 2 : 0;
            assertEquals(inventoryBefore, facility.inventorySnapshot());
            assertEquals(
                expected,
                module.recipeScheduleState()
                    .orderCursor());
            execute(facility, module);
            Item output = policy == NotDoablePolicy.SKIP ? Items.feather : Items.coal;
            assertEquals(1L, facility.itemAmount(ItemStackWrapper.of(new ItemStack(output))));
        }
    }

    // Product regression: a GT zero-sized item input is required, survives save/load, and is not consumed.
    @Test
    void nonConsumedInputMustBePresentButSurvivesRepeatedProduction() {
        AutomatedFacility facility = facility();
        RecipeBook book = orderBook(
            savedRecipe(
                new ItemStack[] { new ItemStack(Items.stick, 0) },
                new ItemStack[] { new ItemStack(Items.iron_ingot) },
                0L));
        book = com.gtnewhorizons.galaxia.core.state.RecipeBookState
            .decode(com.gtnewhorizons.galaxia.core.state.RecipeBookState.encode(book));
        ModuleInstance module = installBook(facility, book);
        ItemStackWrapper catalyst = ItemStackWrapper.of(new ItemStack(Items.stick));
        ItemStackWrapper output = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));

        execute(facility, module);
        assertEquals(0L, facility.itemAmount(output));
        facility.insert(catalyst, 1L);
        execute(facility, module);
        execute(facility, module);

        assertEquals(2L, facility.itemAmount(output));
        assertEquals(1L, facility.itemAmount(catalyst));
    }

    private static ModuleInstance installBook(AutomatedFacility facility, RecipeBook book) {
        FacilityModuleKind kind = FacilityModuleKind.MACERATOR;
        ModuleInstance module = FacilityModuleRegistry.create(
            new ModuleInstance.ID(new UUID(0L, 1L)),
            kind,
            StationTileCoord.of(0, 0),
            kind.defaultShape(),
            kind.defaultTier());
        addModule(facility, module);
        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.ReplaceRecipeBook(facility.assetId, module.id, book),
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
