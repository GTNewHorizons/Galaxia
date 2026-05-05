package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Random;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlotList;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

import sun.misc.Unsafe;

final class ProductionModuleHelperTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
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
        RecipeSlotList slots = new RecipeSlotList();
        slots.add(new RecipeSlot(snapshot, true, 0, Integer.MAX_VALUE, (byte) 1, (byte) 1));
        StubRecipeModule module = new StubRecipeModule(
            new RecipeConfig(slots, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0));

        ProductionModuleHelper.execute(null, station, module, new Random(0), new HashMap<>(), new HashMap<>());

        assertEquals(856, station.inventory.getFluidAmount("galaxia.production.input"));
        assertEquals(72, station.inventory.getFluidAmount("galaxia.production.output"));
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
}
