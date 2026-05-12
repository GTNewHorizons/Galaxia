package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Random;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeList;

import sun.misc.Unsafe;

final class ProductionModuleHelperTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
    }

    @Test
    void executeKeepsPerItemInputLowerBoundAfterCombinedRecipeCost() {
        AutomatedFacility station = station();
        Item inputItem = new Item();
        Item outputItem = new Item();
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.inventory.add(inputResource, 105);

        ItemStack[] inputs = { new ItemStack(inputItem, 2, 0), new ItemStack(inputItem, 4, 0) };
        ItemStack[] outputs = { new ItemStack(outputItem, 1, 0) };
        station.inventory.setItemLowerBound(inputResource, 100);
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

        assertEquals(105, station.inventory.getAmount(inputResource));
        assertEquals(0, station.inventory.getAmount(outputResource));
    }

    @Test
    void executeAllowsOutputUpperBoundOvershootWhenCurrentInventoryIsBelowTarget() {
        AutomatedFacility station = station();
        Item inputItem = new Item();
        Item outputItem = new Item();
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.inventory.add(inputResource, 1);
        station.inventory.add(outputResource, 95);

        ItemStack[] inputs = { new ItemStack(inputItem, 1, 0) };
        ItemStack[] outputs = { new ItemStack(outputItem, 4, 0), new ItemStack(outputItem, 5, 0) };
        station.inventory.setItemUpperBound(outputResource, 100);
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

        assertEquals(0, station.inventory.getAmount(inputResource));
        assertEquals(104, station.inventory.getAmount(outputResource));
    }

    @Test
    void executeUsesCanonicalOutputBoundWhenDuplicateSecondSlotProduces() {
        AutomatedFacility station = station();
        Item inputItem = new Item();
        Item outputItem = new Item();
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.inventory.add(inputResource, 1);
        station.inventory.add(outputResource, 99);

        ItemStack[] inputs = { new ItemStack(inputItem, 1, 0) };
        ItemStack[] outputs = { new ItemStack(outputItem, 1, 0), new ItemStack(outputItem, 2, 0) };
        station.inventory.setItemUpperBound(outputResource, 100);
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

        assertEquals(0, station.inventory.getAmount(inputResource));
        assertEquals(101, station.inventory.getAmount(outputResource));
    }

    @Test
    void executeConsumesAndProducesFluidSnapshotAmounts() throws Exception {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        station.inventory.addFluid("galaxia.production.input", 1000);

        FluidStack[] fluidInputs = { fluidStack("galaxia.production.input", 144) };
        FluidStack[] fluidOutputs = { fluidStack("galaxia.production.output", 72) };
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

        assertEquals(856, station.inventory.getFluidAmount("galaxia.production.input"));
        assertEquals(72, station.inventory.getFluidAmount("galaxia.production.output"));
    }

    @Test
    void executeDoesNotProduceWhenDuplicateItemInputsWouldOverdrawStock() {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        Item inputItem = new Item();
        Item outputItem = new Item();
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.inventory.add(inputResource, 1);

        ItemStack[] inputs = { new ItemStack(inputItem, 1, 0), new ItemStack(inputItem, 1, 0) };
        ItemStack[] outputs = { new ItemStack(outputItem, 1, 0) };
        RecipeSnapshot snapshot = RecipeSnapshot.resolved((byte) 1, 0, inputs, outputs, null, null, 20, 30);
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(new SavedRecipe(snapshot, true, 0L, (byte) 1, (byte) 1));
        StubRecipeModule module = new StubRecipeModule(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));

        ProductionModuleHelper.execute(null, station, module, new Random(0), new HashMap<>(), new HashMap<>());

        assertEquals(1, station.inventory.getAmount(inputResource));
        assertEquals(0, station.inventory.getAmount(outputResource));
    }

    @Test
    void executeKeepsInputLowerBoundAfterConsumingRecipeCost() {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        Item inputItem = new Item();
        Item outputItem = new Item();
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.inventory.add(inputResource, 64);

        ItemStack[] inputs = { new ItemStack(inputItem, 1, 0) };
        ItemStack[] outputs = { new ItemStack(outputItem, 1, 0) };
        RecipeSnapshot snapshot = RecipeSnapshot.resolved((byte) 1, 0, inputs, outputs, null, null, 20, 30);
        station.inventory.setItemLowerBound(inputResource, 64);
        SavedRecipeList slots = new SavedRecipeList();
        slots.add(new SavedRecipe(snapshot, true, 0L, (byte) 1, (byte) 1));
        StubRecipeModule module = new StubRecipeModule(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));

        ProductionModuleHelper.execute(null, station, module, new Random(0), new HashMap<>(), new HashMap<>());

        assertEquals(64, station.inventory.getAmount(inputResource));
        assertEquals(0, station.inventory.getAmount(outputResource));
    }

    @Test
    void executeConsumesInputWhenChancedItemOutputMisses() {
        AutomatedFacility station = station();
        Item inputItem = new Item();
        Item outputItem = new Item();
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.inventory.add(inputResource, 1);

        StubRecipeModule module = itemOutputModule(inputItem, outputItem, 1);

        ProductionModuleHelper.execute(null, station, module, new FixedRandom(5000), new HashMap<>(), new HashMap<>());

        assertEquals(0, station.inventory.getAmount(inputResource));
        assertEquals(0, station.inventory.getAmount(outputResource));
    }

    @Test
    void executeUsesOutputUpperBoundAsCurrentInventoryTargetForItems() {
        Item inputItem = new Item();
        Item outputItem = new Item();
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);

        AutomatedFacility atGuard = station();
        atGuard.inventory.add(inputResource, 1);
        atGuard.inventory.add(outputResource, 1);
        atGuard.inventory.setItemUpperBound(outputResource, 1);
        ProductionModuleHelper.execute(
            null,
            atGuard,
            itemOutputModule(inputItem, outputItem, 1),
            new FixedRandom(4999),
            new HashMap<>(),
            new HashMap<>());

        assertEquals(1, atGuard.inventory.getAmount(inputResource));
        assertEquals(1, atGuard.inventory.getAmount(outputResource));

        AutomatedFacility belowGuard = station();
        belowGuard.inventory.add(inputResource, 1);
        belowGuard.inventory.setItemUpperBound(outputResource, 1);
        ProductionModuleHelper.execute(
            null,
            belowGuard,
            itemOutputModule(inputItem, outputItem, 1),
            new FixedRandom(4999),
            new HashMap<>(),
            new HashMap<>());

        assertEquals(0, belowGuard.inventory.getAmount(inputResource));
        assertEquals(1, belowGuard.inventory.getAmount(outputResource));
    }

    @Test
    void executeUsesOutputUpperBoundAsCurrentInventoryTargetForFluids() throws Exception {
        Item inputItem = new Item();
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);

        AutomatedFacility atGuard = station();
        atGuard.inventory.add(inputResource, 1);
        atGuard.inventory.addFluid("galaxia.production.chanced_output", 72);
        atGuard.inventory.setFluidUpperBound("galaxia.production.chanced_output", 72);
        ProductionModuleHelper.execute(
            null,
            atGuard,
            fluidOutputModule(inputItem, fluidStack("galaxia.production.chanced_output", 72)),
            new FixedRandom(4999),
            new HashMap<>(),
            new HashMap<>());

        assertEquals(1, atGuard.inventory.getAmount(inputResource));
        assertEquals(72, atGuard.inventory.getFluidAmount("galaxia.production.chanced_output"));

        AutomatedFacility belowGuard = station();
        belowGuard.inventory.add(inputResource, 1);
        belowGuard.inventory.setFluidUpperBound("galaxia.production.chanced_output", 72);
        ProductionModuleHelper.execute(
            null,
            belowGuard,
            fluidOutputModule(inputItem, fluidStack("galaxia.production.chanced_output", 72)),
            new FixedRandom(4999),
            new HashMap<>(),
            new HashMap<>());

        assertEquals(0, belowGuard.inventory.getAmount(inputResource));
        assertEquals(72, belowGuard.inventory.getFluidAmount("galaxia.production.chanced_output"));
    }

    @Test
    void executeDoesNotConsumeInputsWhenSelectedItemOutputsWouldOverflowInventory() {
        AutomatedFacility station = station();
        Item fillerItem = new Item();
        Item inputItem = new Item();
        Item outputItem = new Item();
        ItemStackWrapper fillerResource = new ItemStackWrapper(fillerItem, 0, null);
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.inventory.add(fillerResource, 999);
        station.inventory.add(inputResource, 1);

        ProductionModuleHelper.execute(
            null,
            station,
            itemOutputModule(inputItem, outputItem, 2),
            new FixedRandom(4999),
            new HashMap<>(),
            new HashMap<>());

        assertEquals(999, station.inventory.getAmount(fillerResource));
        assertEquals(1, station.inventory.getAmount(inputResource));
        assertEquals(0, station.inventory.getAmount(outputResource));
    }

    @Test
    void executeCanUseFreedInputCapacityForSelectedItemOutputs() {
        AutomatedFacility station = station();
        Item fillerItem = new Item();
        Item inputItem = new Item();
        Item outputItem = new Item();
        ItemStackWrapper fillerResource = new ItemStackWrapper(fillerItem, 0, null);
        ItemStackWrapper inputResource = new ItemStackWrapper(inputItem, 0, null);
        ItemStackWrapper outputResource = new ItemStackWrapper(outputItem, 0, null);
        station.inventory.add(fillerResource, 999);
        station.inventory.add(inputResource, 1);

        ProductionModuleHelper.execute(
            null,
            station,
            itemOutputModule(inputItem, outputItem, 1),
            new FixedRandom(4999),
            new HashMap<>(),
            new HashMap<>());

        assertEquals(999, station.inventory.getAmount(fillerResource));
        assertEquals(0, station.inventory.getAmount(inputResource));
        assertEquals(1, station.inventory.getAmount(outputResource));
    }

    private static AutomatedFacility station() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
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
        Fluid fluid = new Fluid(fluidName);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        FluidStack stack = (FluidStack) unsafe.allocateInstance(FluidStack.class);
        Field fluidField = FluidStack.class.getDeclaredField("fluid");
        fluidField.setAccessible(true);
        fluidField.set(stack, fluid);
        stack.amount = amount;
        return stack;
    }

    private static String fluidName(FluidStack stack) {
        try {
            Fluid fluid = stack.getFluid();
            return fluid != null ? fluid.getName() : null;
        } catch (RuntimeException ignored) {
            try {
                Field fluidField = FluidStack.class.getDeclaredField("fluid");
                fluidField.setAccessible(true);
                Fluid fluid = (Fluid) fluidField.get(stack);
                return fluid != null ? fluid.getName() : null;
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
    }
}
