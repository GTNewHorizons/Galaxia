package com.gtnewhorizons.galaxia.gametests;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.api.RecipeEnvironments;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.recipe.RecipeEnvironment;
import gregtech.api.recipe.RecipeEnvironmentRequirements;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = "gregtech")
public final class RecipeEnvironmentGameTests {

    private RecipeEnvironmentGameTests() {}

    /** Integration contract: native singleblocks reject before consumption and use their native power-fail reset. */
    @GameTest(timeoutTicks = 100)
    public static void singleblockConsumptionAndNativeFailure(GameTestHelper helper) {
        helper.startSequence()
            .thenExecute(() -> {
                for (ItemList type : List.of(ItemList.Machine_LV_Extruder, ItemList.Machine_LV_FluidSolidifier)) {
                    AtomicBoolean allowed = new AtomicBoolean(false);
                    RecipeEnvironment tag = testTag(allowed);
                    BaseMetaTileEntity tile = new BaseMetaTileEntity();
                    tile.setWorldObj(helper.getWorld());
                    tile.setInitialValuesAsNBT(
                        null,
                        (short) type.get(1)
                            .getItemDamage());
                    MTEBasicMachine machine = (MTEBasicMachine) tile.getMetaTileEntity();
                    ItemStack input = new ItemStack(Items.written_book);
                    tile.setInventorySlotContents(machine.getInputSlot(), input);
                    boolean solidifier = type == ItemList.Machine_LV_FluidSolidifier;
                    var builder = GTValues.RA.stdBuilder()
                        .requiresEnvironment(tag)
                        .itemInputs(input.copy())
                        .itemOutputs(new ItemStack(Items.diamond))
                        .duration(80)
                        .eut(8)
                        .noBuffer();
                    if (solidifier) {
                        builder.fluidInputs(new FluidStack(FluidRegistry.WATER, 17));
                        machine.setFillableStack(new FluidStack(FluidRegistry.WATER, 34));
                    } else {
                        builder.itemInputs(input.copy(), new ItemStack(Items.blaze_rod));
                        tile.setInventorySlotContents(machine.getInputSlot() + 1, new ItemStack(Items.blaze_rod));
                    }
                    var recipes = builder.addTo(machine.getRecipeMap());
                    helper.assertFalse(recipes.isEmpty(), "fixture recipe registered");
                    helper.afterTest(
                        () -> recipes.forEach(
                            machine.getRecipeMap()
                                .getBackend()::removeRecipe));
                    helper.assertEquals(1, machine.checkRecipe(), "environment rejects the matching native recipe");
                    helper.assertEquals(1, input.stackSize, "rejected item is unconsumed");
                    if (solidifier)
                        helper.assertEquals(34, machine.getFillableStack().amount, "rejected fluid is unconsumed");

                    allowed.set(true);
                    helper.assertEquals(2, machine.checkRecipe(), "native recipe starts when conditions hold");
                    helper.assertEquals(0, input.stackSize, "accepted input consumed once");
                    tile.setStoredEU(10000);
                    machine.onPostTick(tile, 1);
                    helper.assertTrue(machine.mProgresstime > 0, "permitted operation advances");
                    NBTTagCompound saved = new NBTTagCompound();
                    tile.writeToNBT(saved);
                    helper.assertEquals(
                        RecipeEnvironmentRequirements.of(tag),
                        RecipeEnvironmentRequirements
                            .readFromNBT(saved.getTagList("recipeEnvironments", Constants.NBT.TAG_STRING)),
                        "active unbuffered restriction saved");
                    long energy = tile.getStoredEU();
                    allowed.set(false);
                    machine.onPostTick(tile, 2);
                    helper.assertEquals(
                        -100,
                        machine.mProgresstime,
                        "environment loss enters native insufficient-energy reset");
                    helper.assertEquals(
                        energy,
                        tile.getStoredEU(),
                        "failed environmental tick does not draw process energy");
                }
            })
            .thenSucceed();
    }

    /**
     * Integration contract: EBF's overridden heat validator remains active and runtime failure voids pending outputs.
     */
    @GameTest(timeoutTicks = 100)
    public static void ebfValidationAndNativeShutdown(GameTestHelper helper) {
        helper.startSequence()
            .thenExecute(() -> {
                MetaTileEntity unbound = new MTEElectricBlastFurnace("unbound_environment_test");
                helper.assertFalse(
                    RecipeEnvironments.MICROGRAVITY.isSatisfied(unbound),
                    "unbound controller cannot satisfy microgravity");
                helper.assertFalse(
                    RecipeEnvironments.NO_ATMOSPHERE.isSatisfied(unbound),
                    "unbound controller cannot satisfy vacuum");
                var copied = GTValues.RA.stdBuilder()
                    .requiresEnvironment(RecipeEnvironments.MICROGRAVITY, RecipeEnvironments.NO_ATMOSPHERE)
                    .itemInputs(new ItemStack(Items.paper))
                    .itemOutputs(new ItemStack(Items.book))
                    .duration(40)
                    .eut(8)
                    .specialValue(4500)
                    .build()
                    .orElseThrow()
                    .copy();
                helper.assertEquals(4500, copied.mSpecialValue, "recipe copy retains independent special value");
                helper.assertEquals(
                    RecipeEnvironmentRequirements.of(RecipeEnvironments.MICROGRAVITY, RecipeEnvironments.NO_ATMOSPHERE),
                    copied.getEnvironmentRequirements(),
                    "native recipe copy retains environment metadata");
                helper.assertEquals(
                    copied.getEnvironmentRequirements(),
                    copied.copyShallow()
                        .getEnvironmentRequirements(),
                    "shallow recipe copies retain the same environment conditions");
                AtomicBoolean allowed = new AtomicBoolean(false);
                RecipeEnvironment tag = testTag(allowed);
                Furnace furnace = new Furnace(helper);
                var recipes = GTValues.RA.stdBuilder()
                    .requiresEnvironment(tag)
                    .itemInputs(new ItemStack(Items.written_book))
                    .itemOutputs(new ItemStack(Items.diamond))
                    .duration(80)
                    .eut(8)
                    .noBuffer()
                    .addTo(RecipeMaps.blastFurnaceRecipes);
                helper.assertFalse(recipes.isEmpty(), "EBF fixture recipe registered");
                helper.afterTest(() -> recipes.forEach(RecipeMaps.blastFurnaceRecipes.getBackend()::removeRecipe));
                ItemStack input = new ItemStack(Items.written_book);
                helper.assertFalse(
                    furnace.process(input)
                        .wasSuccessful(),
                    "shared logic rejects before consumption");
                helper.assertEquals(1, input.stackSize, "rejected EBF input unconsumed");
                allowed.set(true);
                helper.assertTrue(
                    furnace.process(input)
                        .wasSuccessful(),
                    "inherited EBF logic accepts satisfied requirements");
                helper.assertEquals(0, input.stackSize, "accepted EBF input consumed");
                NBTTagCompound saved = new NBTTagCompound();
                furnace.getBaseMetaTileEntity()
                    .writeToNBT(saved);
                helper.assertEquals(
                    RecipeEnvironmentRequirements.of(tag),
                    RecipeEnvironmentRequirements
                        .readFromNBT(saved.getTagList("recipeEnvironments", Constants.NBT.TAG_STRING)),
                    "active EBF requirement saved");

                furnace.mMachine = true;
                furnace.mStartUpCheck = -1000;
                furnace.mUpdate = -1000;
                furnace.hasMaintenanceChecks = false;
                furnace.mWrench = furnace.mScrewdriver = furnace.mSoftMallet = furnace.mHardHammer = furnace.mSolderingTool = furnace.mCrowbar = true;
                furnace.mEfficiency = 10000;
                furnace.mMaxProgresstime = 80;
                furnace.mProgresstime = 5;
                furnace.lEUt = 0;
                furnace.mOutputItems = new ItemStack[] { new ItemStack(Items.diamond) };
                furnace.mOutputFluids = new FluidStack[] { new FluidStack(FluidRegistry.WATER, 17) };
                furnace.onPostTick(furnace.getBaseMetaTileEntity(), 1);
                helper.assertTrue(furnace.mProgresstime > 5, "permitted EBF tick advances without requiring energy");
                allowed.set(false);
                furnace.onPostTick(furnace.getBaseMetaTileEntity(), 2);
                helper.assertEquals(0, furnace.mMaxProgresstime, "native shutdown clears operation");
                helper.assertNull(furnace.mOutputItems, "native shutdown discards item outputs");
                helper.assertNull(furnace.mOutputFluids, "native shutdown discards fluid outputs");
                helper.assertFalse(
                    furnace.getBaseMetaTileEntity()
                        .isAllowedToWork(),
                    "native shutdown disables work");

                var hotRecipes = GTValues.RA.stdBuilder()
                    .requiresEnvironment(tag)
                    .itemInputs(new ItemStack(Items.compass))
                    .itemOutputs(new ItemStack(Items.diamond))
                    .duration(80)
                    .eut(8)
                    .specialValue(4000)
                    .addTo(RecipeMaps.blastFurnaceRecipes);
                helper.afterTest(() -> hotRecipes.forEach(RecipeMaps.blastFurnaceRecipes.getBackend()::removeRecipe));
                allowed.set(true);
                ItemStack hotInput = new ItemStack(Items.compass);
                helper.assertFalse(
                    furnace.process(hotInput)
                        .wasSuccessful(),
                    "native heat requirement remains independent");
                helper.assertEquals(1, hotInput.stackSize, "insufficient heat never consumes input");
            })
            .thenSucceed();
    }

    private static RecipeEnvironment testTag(AtomicBoolean allowed) {
        return RecipeEnvironment
            .register(new RecipeEnvironment("galaxia_test:" + UUID.randomUUID(), "galaxia.environment.unknown") {

                @Override
                public boolean isSatisfied(MetaTileEntity machine) {
                    return allowed.get();
                }
            });
    }

    /** Harness exception: supplies ordinary processing inputs without a complete EBF structure or hatch network. */
    private static final class Furnace extends MTEElectricBlastFurnace {

        private Furnace(GameTestHelper helper) {
            super("environment_test_furnace");
            BaseMetaTileEntity tile = new BaseMetaTileEntity();
            tile.setWorldObj(helper.getWorld());
            EntityPlayerMP player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
            tile.setOwnerName(player.getCommandSenderName());
            tile.setOwnerUuid(player.getUniqueID());
            tile.setMetaTileEntity(this);
            setBaseMetaTileEntity(tile);
        }

        private CheckRecipeResult process(ItemStack input) {
            processingLogic.clear();
            return processingLogic.setMachine(this)
                .setRecipeMapSupplier(() -> RecipeMaps.blastFurnaceRecipes)
                .setInputItems(input)
                .setAvailableVoltage(32)
                .setAvailableAmperage(1)
                .process();
        }
    }
}
