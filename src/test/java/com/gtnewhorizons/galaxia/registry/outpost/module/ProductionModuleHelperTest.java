package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Random;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeList;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;
import com.gtnewhorizons.galaxia.testing.TestFluidStacks;

final class ProductionModuleHelperTest {

    private static Fluid TEST_FLUID_1;
    private static Fluid TEST_FLUID_2;

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();

        TEST_FLUID_1 = FluidRegistry.WATER;
        TEST_FLUID_2 = FluidRegistry.LAVA;
    }

    @Test
    void executeKeepsPerItemInputLowerBoundAfterCombinedRecipeCost() {
        AutomatedFacility station = station();
        Item inputItem = Items.diamond;
        Item outputItem = Items.iron_ingot;
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.insert(inputResource, 105);

        ItemStack[] inputs = { new ItemStack(inputItem, 2, 0), new ItemStack(inputItem, 4, 0) };
        ItemStack[] outputs = { new ItemStack(outputItem, 1, 0) };
        station.setBound(inputResource, 100, true);
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(
            new SavedRecipe(
                RecipeSnapshot.resolved((byte) 1, 0, inputs, outputs, null, null, 20, 30),
                true,
                0L,
                (byte) 1,
                (byte) 1));
        StubRecipeModule module = new StubRecipeModule(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));

        ProductionModuleHelper.execute(null, station, module, new Random(0), new HashMap<>(), new HashMap<>());

        assertEquals(105, station.itemAmount(inputResource));
        assertEquals(0, station.itemAmount(outputResource));
    }

    @Test
    void executeDoesNotConsumeInputWhenSelectedOutputIsRejectedByInsertionFilter() {
        AutomatedFacility station = station();
        ItemStackWrapper input = new ItemStackWrapper(Items.diamond, 0, null);
        ItemStackWrapper output = new ItemStackWrapper(Items.iron_ingot, 0, null);
        station.insert(input, 1L);
        station.addFilter(
            new ItemStackWrapper(Items.gold_ingot, 0, null).toItemStack()
                .getUnlocalizedName(),
            true);
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(
            new SavedRecipe(
                RecipeSnapshot.resolved(
                    (byte) 1,
                    0,
                    new ItemStack[] { new ItemStack(Items.diamond) },
                    new ItemStack[] { new ItemStack(Items.iron_ingot) },
                    null,
                    null,
                    20,
                    30),
                true,
                0L,
                (byte) 1,
                (byte) 1));
        StubRecipeModule module = new StubRecipeModule(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));
        int revisionBefore = station.getStateRevision();

        ProductionModuleHelper.execute(null, station, module, new Random(0), new HashMap<>(), new HashMap<>());

        assertEquals(1L, station.itemAmount(input));
        assertEquals(0L, station.itemAmount(output));
        assertEquals(revisionBefore, station.getStateRevision());
    }

    @Test
    void executeAllowsOutputUpperBoundOvershootWhenCurrentInventoryIsBelowTarget() {
        AutomatedFacility station = station();
        Item inputItem = Items.diamond;
        Item outputItem = Items.iron_ingot;
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.insert(inputResource, 1);
        station.insert(outputResource, 95);

        ItemStack[] inputs = { new ItemStack(inputItem, 1, 0) };
        ItemStack[] outputs = { new ItemStack(outputItem, 4, 0), new ItemStack(outputItem, 5, 0) };
        station.setBound(outputResource, 100, false);
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(
            new SavedRecipe(
                RecipeSnapshot.resolved((byte) 1, 0, inputs, outputs, null, null, 20, 30),
                true,
                0L,
                (byte) 1,
                (byte) 1));
        StubRecipeModule module = new StubRecipeModule(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));
        int revisionBefore = station.getStateRevision();

        ProductionModuleHelper.execute(null, station, module, new Random(0), new HashMap<>(), new HashMap<>());

        assertEquals(0, station.itemAmount(inputResource));
        assertEquals(104, station.itemAmount(outputResource));
        assertEquals(revisionBefore + 1, station.getStateRevision());
    }

    @Test
    void executeUsesCanonicalOutputBoundWhenDuplicateSecondSlotProduces() {
        AutomatedFacility station = station();
        Item inputItem = Items.diamond;
        Item outputItem = Items.iron_ingot;
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.insert(inputResource, 1);
        station.insert(outputResource, 99);

        ItemStack[] inputs = { new ItemStack(inputItem, 1, 0) };
        ItemStack[] outputs = { new ItemStack(outputItem, 1, 0), new ItemStack(outputItem, 2, 0) };
        station.setBound(outputResource, 100, false);
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(
            new SavedRecipe(
                RecipeSnapshot.resolved((byte) 1, 0, inputs, outputs, null, null, new int[] { 0, 10_000 }, 20, 30),
                true,
                0L,
                (byte) 1,
                (byte) 1));
        StubRecipeModule module = new StubRecipeModule(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));

        ProductionModuleHelper.execute(null, station, module, new Random(0), new HashMap<>(), new HashMap<>());

        assertEquals(0, station.itemAmount(inputResource));
        assertEquals(101, station.itemAmount(outputResource));
    }

    @Test
    void executeConsumesAndProducesFluidSnapshotAmounts() throws Exception {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        FluidKey inputKey = new FluidKey(TEST_FLUID_1, null);
        station.insert(inputKey, 1000);

        FluidStack[] fluidInputs = { new FluidStack(TEST_FLUID_1, 144) };
        FluidStack[] fluidOutputs = { new FluidStack(TEST_FLUID_2, 72) };
        RecipeSnapshot snapshot = new RecipeSnapshot(
            (byte) 1,
            0,
            RecipeSnapshot.computeContentHash(null, null, fluidInputs, fluidOutputs, 20, 30),
            null,
            null,
            fluidInputs,
            fluidOutputs,
            20,
            30);
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(new SavedRecipe(snapshot, true, 0L, (byte) 1, (byte) 1));
        StubRecipeModule module = new StubRecipeModule(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));

        ProductionModuleHelper.execute(null, station, module, new Random(0), new HashMap<>(), new HashMap<>());

        assertEquals(856, station.fluidAmount(new FluidKey(TEST_FLUID_1, null)));
        assertEquals(72, station.fluidAmount(new FluidKey(TEST_FLUID_2, null)));
    }

    @Test
    void executeDoesNotProduceWhenDuplicateItemInputsWouldOverdrawStock() {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        Item inputItem = Items.diamond;
        Item outputItem = Items.iron_ingot;
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.insert(inputResource, 1);

        ItemStack[] inputs = { new ItemStack(inputItem, 1, 0), new ItemStack(inputItem, 1, 0) };
        ItemStack[] outputs = { new ItemStack(outputItem, 1, 0) };
        RecipeSnapshot snapshot = RecipeSnapshot.resolved((byte) 1, 0, inputs, outputs, null, null, 20, 30);
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(new SavedRecipe(snapshot, true, 0L, (byte) 1, (byte) 1));
        StubRecipeModule module = new StubRecipeModule(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));

        ProductionModuleHelper.execute(null, station, module, new Random(0), new HashMap<>(), new HashMap<>());

        assertEquals(1, station.itemAmount(inputResource));
        assertEquals(0, station.itemAmount(outputResource));
    }

    @Test
    void executeKeepsInputLowerBoundAfterConsumingRecipeCost() {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        Item inputItem = Items.diamond;
        Item outputItem = Items.iron_ingot;
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.insert(inputResource, 64);

        ItemStack[] inputs = { new ItemStack(inputItem, 1, 0) };
        ItemStack[] outputs = { new ItemStack(outputItem, 1, 0) };
        RecipeSnapshot snapshot = RecipeSnapshot.resolved((byte) 1, 0, inputs, outputs, null, null, 20, 30);
        station.setBound(inputResource, 64, true);
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(new SavedRecipe(snapshot, true, 0L, (byte) 1, (byte) 1));
        StubRecipeModule module = new StubRecipeModule(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));

        ProductionModuleHelper.execute(null, station, module, new Random(0), new HashMap<>(), new HashMap<>());

        assertEquals(64, station.itemAmount(inputResource));
        assertEquals(0, station.itemAmount(outputResource));
    }

    @Test
    void executeKeepsInputAboveManualLowerBoundPlusUpkeepReserve() {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        Item inputItem = Items.diamond;
        Item outputItem = Items.iron_ingot;
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.insert(inputResource, 11);
        station.setBound(inputResource, 1, true);
        station.setUpkeepReserve(inputResource, 10L);

        ItemStack[] inputs = { new ItemStack(inputItem, 1, 0) };
        ItemStack[] outputs = { new ItemStack(outputItem, 1, 0) };
        RecipeSnapshot snapshot = RecipeSnapshot.resolved((byte) 1, 0, inputs, outputs, null, null, 20, 30);
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(new SavedRecipe(snapshot, true, 0L, (byte) 1, (byte) 1));
        StubRecipeModule module = new StubRecipeModule(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));

        ProductionModuleHelper.execute(null, station, module, new Random(0), new HashMap<>(), new HashMap<>());

        assertEquals(11, station.itemAmount(inputResource));
        assertEquals(0, station.itemAmount(outputResource));
    }

    @Test
    void executeConsumesInputWhenChancedItemOutputMisses() {
        AutomatedFacility station = station();
        Item inputItem = Items.diamond;
        Item outputItem = Items.iron_ingot;
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.insert(inputResource, 1);

        StubRecipeModule module = itemOutputModule(inputItem, outputItem, 1);

        ProductionModuleHelper.execute(null, station, module, new FixedRandom(5000), new HashMap<>(), new HashMap<>());

        assertEquals(0, station.itemAmount(inputResource));
        assertEquals(0, station.itemAmount(outputResource));
    }

    @Test
    void executeUsesOutputUpperBoundAsCurrentInventoryTargetForItems() {
        Item inputItem = Items.diamond;
        Item outputItem = Items.iron_ingot;
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);

        AutomatedFacility atGuard = station();
        atGuard.insert(inputResource, 1);
        atGuard.insert(outputResource, 1);
        atGuard.setBound(outputResource, 1, false);
        ProductionModuleHelper.execute(
            null,
            atGuard,
            itemOutputModule(inputItem, outputItem, 1),
            new FixedRandom(4999),
            new HashMap<>(),
            new HashMap<>());

        assertEquals(1, atGuard.itemAmount(inputResource));
        assertEquals(1, atGuard.itemAmount(outputResource));

        AutomatedFacility belowGuard = station();
        belowGuard.insert(inputResource, 1);
        belowGuard.setBound(outputResource, 1, false);
        ProductionModuleHelper.execute(
            null,
            belowGuard,
            itemOutputModule(inputItem, outputItem, 1),
            new FixedRandom(4999),
            new HashMap<>(),
            new HashMap<>());

        assertEquals(0, belowGuard.itemAmount(inputResource));
        assertEquals(1, belowGuard.itemAmount(outputResource));
    }

    @Test
    void executeUsesOutputUpperBoundAsCurrentInventoryTargetForFluids() throws Exception {
        Item inputItem = Items.diamond;
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);

        AutomatedFacility atGuard = station();
        atGuard.insert(inputResource, 1);
        FluidKey outputKey = new FluidKey(TEST_FLUID_1, null);
        atGuard.insert(outputKey, 72);
        atGuard.setBound(outputKey, 72, false);
        ProductionModuleHelper.execute(
            null,
            atGuard,
            fluidOutputModule(inputItem, new FluidStack(TEST_FLUID_1, 72)),
            new FixedRandom(4999),
            new HashMap<>(),
            new HashMap<>());

        assertEquals(1, atGuard.itemAmount(inputResource));
        assertEquals(72, atGuard.fluidAmount(new FluidKey(TEST_FLUID_1, null)));

        AutomatedFacility belowGuard = station();
        belowGuard.insert(inputResource, 1);
        belowGuard.setBound(new FluidKey(TEST_FLUID_1, null), 72, false);
        ProductionModuleHelper.execute(
            null,
            belowGuard,
            fluidOutputModule(inputItem, new FluidStack(TEST_FLUID_1, 72)),
            new FixedRandom(4999),
            new HashMap<>(),
            new HashMap<>());

        assertEquals(0, belowGuard.itemAmount(inputResource));
        assertEquals(72, belowGuard.fluidAmount(new FluidKey(TEST_FLUID_1, null)));
    }

    @Test
    void executeDoesNotConsumeInputsWhenSelectedItemOutputsWouldOverflowInventory() {
        AutomatedFacility station = station();
        Item fillerItem = Items.diamond;
        Item inputItem = Items.iron_ingot;
        Item outputItem = Items.gold_ingot;
        ItemStackWrapper fillerResource = new ItemStackWrapper(fillerItem, 0, null);
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.insert(fillerResource, 999);
        station.insert(inputResource, 1);

        ProductionModuleHelper.execute(
            null,
            station,
            itemOutputModule(inputItem, outputItem, 2),
            new FixedRandom(4999),
            new HashMap<>(),
            new HashMap<>());

        assertEquals(999, station.itemAmount(fillerResource));
        assertEquals(1, station.itemAmount(inputResource));
        assertEquals(0, station.itemAmount(outputResource));
    }

    @Test
    void executeCanUseFreedInputCapacityForSelectedItemOutputs() {
        AutomatedFacility station = station();
        Item fillerItem = Items.diamond;
        Item inputItem = Items.iron_ingot;
        Item outputItem = Items.gold_ingot;
        ItemStackWrapper fillerResource = new ItemStackWrapper(fillerItem, 0, null);
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.insert(fillerResource, 999);
        station.insert(inputResource, 1);

        ProductionModuleHelper.execute(
            null,
            station,
            itemOutputModule(inputItem, outputItem, 1),
            new FixedRandom(4999),
            new HashMap<>(),
            new HashMap<>());

        assertEquals(999, station.itemAmount(fillerResource));
        assertEquals(0, station.itemAmount(inputResource));
        assertEquals(1, station.itemAmount(outputResource));
    }

    private static AutomatedFacility station() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static StubRecipeModule itemOutputModule(Item inputItem, Item outputItem, int outputSize) {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(
            new SavedRecipe(
                RecipeSnapshot.resolved(
                    (byte) 1,
                    0,
                    new ItemStack[] { new ItemStack(inputItem, 1, 0) },
                    new ItemStack[] { new ItemStack(outputItem, outputSize, 0) },
                    null,
                    null,
                    new int[] { 5000 },
                    20,
                    30),
                true,
                0L,
                (byte) 1,
                (byte) 1));
        return new StubRecipeModule(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));
    }

    private static StubRecipeModule fluidOutputModule(Item inputItem, FluidStack output) {
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(
            new SavedRecipe(
                RecipeSnapshot.resolved(
                    (byte) 1,
                    0,
                    new ItemStack[] { new ItemStack(inputItem, 1, 0) },
                    null,
                    null,
                    new FluidStack[] { output },
                    null,
                    new int[] { 5000 },
                    20,
                    30),
                true,
                0L,
                (byte) 1,
                (byte) 1));
        return new StubRecipeModule(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));
    }

    private static final class StubRecipeModule implements IRecipeModule {

        private RecipeConfig config;

        private StubRecipeModule(RecipeConfig config) {
            this.config = config;
        }

        @Override
        public String getRecipeMapName() {
            return "gt.recipe.invalid";
        }

        @Override
        public RecipeConfig getRecipeConfig() {
            return config;
        }

        @Override
        public void setRecipeConfig(RecipeConfig config) {
            this.config = config;
        }
    }

    private static final class FixedRandom extends Random {

        private final int value;

        private FixedRandom(int value) {
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            return value;
        }
    }

    private static FluidStack fluidStack(String fluidName, int amount) throws Exception {
        return TestFluidStacks.stack(fluidName, amount);
    }

    private static String fluidName(FluidStack stack) {
        return TestFluidStacks.name(stack);
    }
}
