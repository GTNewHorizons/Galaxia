package com.gtnewhorizons.galaxia.gametests;

import static com.gtnewhorizons.galaxia.gt5.OrbitalItems.*;
import static gregtech.api.recipe.RecipeMaps.*;

import java.util.Arrays;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.RecipeEnvironments;
import com.gtnewhorizons.galaxia.gt5.OrbitalItems;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech" })
public final class OrbitalProductionGameTests {

    private OrbitalProductionGameTests() {}

    /** Integration contract: orbital ingredients are usable stacks and their finishing routes use standard GT maps. */
    @GameTest(timeoutTicks = 100)
    public static void registeredContentAndFinishingRecipes(GameTestHelper helper) {
        for (OrbitalItems item : OrbitalItems.values()) {
            ItemStack first = item.get(1);
            ItemStack second = item.get(2);
            if (first.getItem() == null || second.stackSize != 2 || first.stackSize != 1 || first == second) {
                throw new AssertionError("Invalid independently allocated orbital stack: " + item);
            }
        }
        recipe(mixerRecipes, SIGE_BLEND);
        recipe(blastFurnaceRecipes, SIGE_SEED, SIGE_BLEND);
        recipe(blastFurnaceRecipes, SIGE_MONOCRYSTAL, SIGE_SEED);
        recipe(blastFurnaceRecipes, null, FLUORIDE_GLASS_MIX);
        recipe(fluidSolidifierRecipes, FLUORIDE_GLASS_BLANK);
        recipe(blastFurnaceRecipes, HEATED_FLUORIDE_GLASS_PREFORM, FLUORIDE_GLASS_PREFORM);
        recipe(extruderRecipes, FLUORIDE_OPTICAL_FIBER, HEATED_FLUORIDE_GLASS_PREFORM);
        recipe(fluidExtractionRecipes, null, POLY_SIGE);
        recipe(fluidExtractionRecipes, null, SIGE_SEED);
        recipe(hammerRecipes, SIGE_SEED);
        recipe(cutterRecipes, SIGE_WAFER);
        recipe(chemicalBathRecipes, ETCHED_CLC_WAFER);
        recipe(assemblerRecipes, CLC_MODULE);
        recipe(latheRecipes, FLUORIDE_GLASS_PREFORM);
        recipe(assemblerRecipes, FLUORIDE_FIBER_SPOOL);
        recipe(laserEngraverRecipes, SOLAR_CELL);
        recipe(cutterRecipes, SOLAR_GLASS_SHEET);
        recipe(assemblerRecipes, SPACE_GRADE_SOLAR_PANEL);
        recipe(assemblerRecipes, PRIMITIVE_TELESCOPE_SATELLITE);
        recipe(assemblerRecipes, COMMUNICATION_SATELLITE);
        recipe(assemblerRecipes, PROSPECTING_SATELLITE);
        helper.succeed();
    }

    /** Integration contract: electronics retain native cleanroom gating and untreated blend cannot bypass treatment. */
    @GameTest(timeoutTicks = 100)
    public static void cleanroomAndTreatmentCannotBeBypassed(GameTestHelper helper) {
        for (GTRecipe recipe : new GTRecipe[] { recipe(laserEngraverRecipes, CLC_WAFER),
            recipe(cutterRecipes, CLC_CHIP) }) {
            if (!recipe.getMetadataOrDefault(GTRecipeConstants.CLEANROOM, false)) {
                throw new AssertionError("CLC processing lost native cleanroom requirement");
            }
        }
        for (GTRecipe recipe : fluidExtractionRecipes.getAllRecipes()) {
            if (contains(recipe.mInputs, SIGE_BLEND)) {
                throw new AssertionError("Untreated SiGe blend has a melting bypass");
            }
        }
        GTRecipe powder = recipe(blastFurnaceRecipes, SIGE_SEED, SIGE_BLEND);
        if (!contains(powder.mOutputs, POLY_SIGE) || powder.getOutputChance(0) != 1000
            || powder.getOutputChance(1) != 9000
            || powder.getOutputChance(2) != 10000) {
            throw new AssertionError("Powder treatment lost independent seed/poly rolls or guaranteed residue");
        }
        for (GTRecipe hot : new GTRecipe[] { powder, recipe(blastFurnaceRecipes, SIGE_MONOCRYSTAL, SIGE_SEED),
            recipe(blastFurnaceRecipes, null, FLUORIDE_GLASS_MIX),
            recipe(blastFurnaceRecipes, HEATED_FLUORIDE_GLASS_PREFORM, FLUORIDE_GLASS_PREFORM) }) {
            if (hot.mSpecialValue <= 0) throw new AssertionError("Orbital EBF operation lost coil heat requirement");
        }
        for (GTRecipe orbital : new GTRecipe[] { powder, recipe(blastFurnaceRecipes, SIGE_MONOCRYSTAL, SIGE_SEED),
            recipe(blastFurnaceRecipes, null, FLUORIDE_GLASS_MIX), recipe(fluidSolidifierRecipes, FLUORIDE_GLASS_BLANK),
            recipe(blastFurnaceRecipes, HEATED_FLUORIDE_GLASS_PREFORM, FLUORIDE_GLASS_PREFORM),
            recipe(extruderRecipes, FLUORIDE_OPTICAL_FIBER, HEATED_FLUORIDE_GLASS_PREFORM) }) {
            if (!RecipeEnvironments.of(orbital)
                .equals(RecipeEnvironments.Requirement.of(RecipeEnvironments.MICROGRAVITY))) {
                throw new AssertionError("Orbital processing lost its microgravity requirement");
            }
        }
        GTRecipe shaping = recipe(latheRecipes, FLUORIDE_GLASS_PREFORM);
        if (!contains(shaping.mOutputs, FLUORIDE_GLASS_MIX)) {
            throw new AssertionError("Preform shaping lost reclaimed feedstock");
        }
        GTRecipe opening = recipe(assemblerRecipes, PRIMITIVE_TELESCOPE_SATELLITE);
        if (contains(opening.mInputs, CLC_MODULE) || contains(opening.mInputs, FLUORIDE_FIBER_SPOOL)
            || contains(opening.mInputs, SPACE_GRADE_SOLAR_PANEL)) {
            throw new AssertionError("Opening satellite requires orbital manufacturing");
        }
        GTRecipe starter = recipe(assemblerRecipes, SPACE_GRADE_SOLAR_PANEL, FLUORIDE_GLASS_MIX);
        if (contains(starter.mInputs, CLC_MODULE) || contains(starter.mInputs, SIGE_WAFER)
            || contains(starter.mInputs, FLUORIDE_GLASS_BLANK)) {
            throw new AssertionError("Ground solar bootstrap requires orbital intermediates");
        }
        helper.succeed();
    }

    private static GTRecipe recipe(RecipeMap<?> map, OrbitalItems output, OrbitalItems... inputs) {
        return map.getAllRecipes()
            .stream()
            .filter(recipe -> output == null || contains(recipe.mOutputs, output))
            .filter(
                recipe -> Arrays.stream(inputs)
                    .allMatch(input -> contains(recipe.mInputs, input)))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing orbital recipe in " + map.unlocalizedName));
    }

    private static boolean contains(ItemStack[] stacks, OrbitalItems item) {
        return Arrays.stream(stacks)
            .anyMatch(stack -> stack != null && stack.isItemEqual(item.get(1)));
    }
}
