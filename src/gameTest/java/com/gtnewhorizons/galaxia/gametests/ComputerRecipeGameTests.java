package com.gtnewhorizons.galaxia.gametests;

import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.recipe.RecipeMaps.vacuumFreezerRecipes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.galaxia.gt5.OrbitalItems;
import com.gtnewhorizons.galaxia.gt5.computer.ComputerContent;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.util.GTRecipe;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech" })
public final class ComputerRecipeGameTests {

    private ComputerRecipeGameTests() {}

    /** Player contract: hardware requires the complete material chain, one base block and rack-only solder. */
    @GameTest(timeoutTicks = 100)
    public static void hardwareAssemblyRequiresTheCompleteMaterialChain(GameTestHelper helper) {
        List<ItemStack> hardware = new ArrayList<>(
            List.of(
                ComputerContent.controller(),
                ComputerContent.cluster(),
                ComputerContent.radiator(),
                ComputerContent.port(),
                ComputerContent.cable()));
        for (int meta = 0; meta < 4; meta++) hardware.add(new ItemStack(ComputerContent.hardware, 1, meta));
        boolean supplied = List.of("plateSpaceGradeAlloy", "frameGtSpaceGradeAlloy", "foilSpaceGradeAlloy")
            .stream()
            .noneMatch(
                name -> OreDictionary.getOres(name)
                    .isEmpty());
        List<String> missing = new ArrayList<>();
        for (ItemStack output : hardware) {
            GTRecipe recipe = assemblerRecipes.getAllRecipes()
                .stream()
                .filter(candidate -> contains(candidate.mOutputs, output))
                .findFirst()
                .orElse(null);
            if (!supplied) {
                helper.assertTrue(recipe == null, output.getDisplayName() + " can be assembled without SGA");
                continue;
            }
            if (recipe == null) {
                missing.add(output.getDisplayName());
                continue;
            }
            int blocks = Arrays.stream(recipe.mInputs)
                .filter(stack -> stack != null && stack.getItem() instanceof ItemBlock)
                .mapToInt(stack -> stack.stackSize)
                .sum();
            helper.assertTrue(blocks <= 1, output.getDisplayName() + " consumes several base blocks");
            boolean rack = output.getItem() == new ItemStack(ComputerContent.hardware).getItem()
                && output.getItemDamage() < 3;
            if (rack) {
                helper.assertTrue(
                    recipe.mFluidInputs.length == 1
                        && recipe.mFluidInputs[0].isFluidEqual(Materials.SolderingAlloy.getMolten(1)),
                    "Rack assembly must use Soldering Alloy");
                for (ItemStack input : List.of(
                    OrbitalItems.CLC_MODULE.get(1),
                    OrbitalItems.FLUORIDE_FIBER_SPOOL.get(1),
                    ItemList.Circuit_Chip_Ram.get(1))) {
                    helper.assertTrue(contains(recipe.mInputs, input), "Rack lost " + input.getDisplayName());
                }
            } else {
                helper.assertTrue(recipe.mFluidInputs.length == 0, output.getDisplayName() + " requires a fluid");
            }
            int produced = Arrays.stream(recipe.mOutputs)
                .filter(stack -> stack != null && stack.isItemEqual(output))
                .mapToInt(stack -> stack.stackSize)
                .sum();
            helper.assertTrue(
                produced == (output.isItemEqual(ComputerContent.cable()) ? 8 : 1),
                "Wrong hardware batch size for " + output.getDisplayName());
        }
        helper.assertTrue(missing.isEmpty(), "Missing hardware recipes, check Space Grade Alloy forms: " + missing);
        helper.succeed();
    }

    /** Player contract: active cooling consumes EU, conserves coolant and supplies the same fluid used by racks. */
    @GameTest(timeoutTicks = 100)
    public static void activeCoolingClosesTheBinaryFluidLoop(GameTestHelper helper) {
        GTRecipe recipe = vacuumFreezerRecipes.getAllRecipes()
            .stream()
            .filter(candidate -> candidate.mFluidInputs.length == 1 && candidate.mFluidOutputs.length == 1)
            .filter(candidate -> candidate.mFluidInputs[0].isFluidEqual(ComputerContent.neutral(1)))
            .filter(candidate -> candidate.mFluidOutputs[0].isFluidEqual(ComputerContent.chilled(1)))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No active cooling recipe produces the Computer's chilled coolant"));
        helper.assertTrue(
            recipe.mFluidInputs[0].amount == recipe.mFluidOutputs[0].amount,
            "Active cooling creates or loses coolant");
        helper.assertTrue(recipe.mEUt > 0 && recipe.mDuration > 0, "Active cooling must consume power over time");
        helper.succeed();
    }

    private static boolean contains(ItemStack[] stacks, ItemStack expected) {
        return Arrays.stream(stacks)
            .anyMatch(stack -> stack != null && stack.isItemEqual(expected));
    }
}
