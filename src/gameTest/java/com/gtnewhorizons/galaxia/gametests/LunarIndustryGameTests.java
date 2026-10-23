package com.gtnewhorizons.galaxia.gametests;

import java.util.HashSet;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.core.nei.CraterDistributionHandler;
import com.gtnewhorizons.galaxia.gt5.LunarMaterials;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterProfile;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterSize;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import codechicken.nei.recipe.GuiCraftingRecipe;
import gregtech.api.enums.ItemList;
import gregtech.api.recipe.RecipeMaps;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech", "NotEnoughItems" })
public final class LunarIndustryGameTests {

    private LunarIndustryGameTests() {}

    @GameTest(timeoutTicks = 300)
    public static void registeredSeparationAndCraterDistribution(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .server("check actual registered profile fluids and separator recipes", () -> {
                var identities = new HashSet<Fluid>();
                for (CraterProfile profile : CraterProfile.values()) {
                    Fluid fluid = LunarMaterials.enriched(profile);
                    if (fluid == null || !identities.add(fluid)) throw new AssertionError("Profile fluid identity");
                    boolean registered = RecipeMaps.centrifugeRecipes.getAllRecipes()
                        .stream()
                        .anyMatch(
                            recipe -> recipe.mFluidInputs.length == 1 && recipe.mFluidInputs[0].getFluid() == fluid
                                && recipe.mFluidInputs[0].amount == 16000
                                && recipe.mOutputs.length > 0
                                && recipe.mFluidOutputs.length == 1
                                && recipe.mFluidOutputs[0].amount == 16000
                                && recipe.mFluidOutputs[0].getFluid() == LunarMaterials.spentCarrier);
                    if (!registered) throw new AssertionError("Missing separator recipe for " + profile);
                    var selected = RecipeMaps.centrifugeRecipes.findRecipeQuery()
                        .fluids(new FluidStack(fluid, 16000))
                        .voltage(120)
                        .find();
                    if (selected == null || selected.mFluidInputs[0].getFluid() != fluid) {
                        throw new AssertionError("GT recipe lookup cannot select the separator for " + profile);
                    }
                }
                var cleanup = RecipeMaps.centrifugeRecipes.findRecipeQuery()
                    .fluids(new FluidStack(LunarMaterials.spentCarrier, 16000))
                    .voltage(120)
                    .find();
                if (cleanup == null || cleanup.mFluidOutputs.length != 1
                    || cleanup.mFluidOutputs[0].getFluid() != ItemList.sDrillingFluid
                    || cleanup.mFluidOutputs[0].amount != cleanup.mFluidInputs[0].amount) {
                    throw new AssertionError("Carrier cleanup must return usable Drilling Fluid without volume loss");
                }
            })
            .client("open registered NEI crater category", client -> {
                var handler = new CraterDistributionHandler();
                handler.loadCraftingRecipes("galaxia.crater_distribution");
                if (handler.numRecipes() != CraterSize.values().length)
                    throw new AssertionError("Expected one page per crater size");
                Minecraft.getMinecraft().gameSettings.guiScale = 2;
                GuiCraftingRecipe.openRecipeGui("galaxia.crater_distribution");
            })
            .awaitClient("NEI crater page is open", client -> {
                if (!(Minecraft.getMinecraft().currentScreen instanceof GuiCraftingRecipe)) {
                    throw new AssertionError("Crater distribution page did not open");
                }
            })
            .capture("lunar-crater-distribution")
            .succeed();
    }
}
