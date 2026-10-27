package com.gtnewhorizons.galaxia.gt5;

import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.recipe.RecipeMaps.blastFurnaceRecipes;
import static gregtech.api.recipe.RecipeMaps.centrifugeRecipes;
import static gregtech.api.recipe.RecipeMaps.chemicalReactorRecipes;
import static gregtech.api.recipe.RecipeMaps.distilleryRecipes;
import static gregtech.api.recipe.RecipeMaps.electrolyzerRecipes;
import static gregtech.api.recipe.RecipeMaps.multiblockChemicalReactorRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.COIL_HEAT;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.api.RecipeEnvironments;
import com.gtnewhorizons.galaxia.compat.gt.crater.MTECraterExtractor;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterProfile;
import com.ruling_0.materiallib.api.Material;
import com.ruling_0.materiallib.api.MaterialLibAPI;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Circuits;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.materials.Materials;
import gregtech.api.enums.materials.Shapes;
import gregtech.api.material.MaterialUtils;

/** Initial lunar extraction and refining recipes using ordinary GT recipe maps. */
public final class CraterRecipes {

    private CraterRecipes() {}

    public static ItemStack[] solids(CraterProfile profile) {
        return switch (profile) {
            case IRON_SILICA -> new ItemStack[] { crushed(Materials.BandedIron, 8), crushed(Materials.Quartzite, 2) };
            case GOLD_COBALT -> new ItemStack[] { crushed(Materials.Gold, 2), crushed(Materials.Cobaltite, 1) };
            case SCANDIUM_BAUXITE -> new ItemStack[] { crushed(Materials.Bauxite, 8),
                MaterialLibAPI.getStack(CraterMaterials.scandiumConcentrate, Shapes.dust, 1) };
            case PLATINUM -> new ItemStack[] { crushed(Materials.Platinum, 4) };
        };
    }

    public static void register() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_HV.get(1),
                ItemList.Electric_Motor_HV.get(2),
                ItemList.Electric_Pump_HV.get(1),
                ItemList.Electric_Piston_HV.get(2),
                Circuits.HV.get(2),
                MaterialLibAPI.getStack(Materials.Steel, Shapes.plate, 4))
            .fluidInputs(MaterialUtils.molten(Materials.Tin, 144))
            .itemOutputs(GregTechAPI.METATILEENTITIES[MTECraterExtractor.ID].getStackForm(1))
            .duration(45 * SECONDS)
            .eut(480)
            .addTo(assemblerRecipes);
        for (CraterProfile profile : CraterProfile.values()) {
            GTValues.RA.stdBuilder()
                .fluidInputs(new FluidStack(CraterMaterials.enriched(profile), 16000))
                .itemOutputs(solids(profile))
                .fluidOutputs(new FluidStack(CraterMaterials.spentCarrier, 16000))
                .duration(40 * SECONDS)
                .eut(120)
                .addTo(centrifugeRecipes);
        }
        GTValues.RA.stdBuilder()
            .fluidInputs(new FluidStack(CraterMaterials.spentCarrier, 4000))
            .fluidOutputs(new FluidStack(ItemList.sDrillingFluid, 4000))
            .duration(8 * SECONDS)
            .eut(120)
            .addTo(centrifugeRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(MaterialLibAPI.getStack(CraterMaterials.scandiumConcentrate, Shapes.dust, 12))
            .fluidInputs(MaterialUtils.fluid(Materials.NitricAcid, 6000), MaterialUtils.fluid(Materials.Water, 3000))
            .itemOutputs(MaterialLibAPI.getStack(Materials.Stone, Shapes.dust, 10))
            .fluidOutputs(new FluidStack(CraterMaterials.scandiumSolution, 9000))
            .duration(60 * SECONDS)
            .eut(480)
            .addTo(multiblockChemicalReactorRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(MaterialLibAPI.getStack(Materials.SodiumHydroxide, Shapes.dust, 18))
            .fluidInputs(new FluidStack(CraterMaterials.scandiumSolution, 9000))
            .itemOutputs(MaterialLibAPI.getStack(CraterMaterials.scandiumOxide, Shapes.dust, 5))
            .fluidOutputs(new FluidStack(CraterMaterials.spentLeachSolution, 9000))
            .duration(30 * SECONDS)
            .eut(120)
            .addTo(chemicalReactorRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                MaterialLibAPI.getStack(CraterMaterials.scandiumOxide, Shapes.dust, 5),
                MaterialLibAPI.getStack(Materials.Calcium, Shapes.dust, 3))
            .itemOutputs(
                MaterialLibAPI.getStack(Materials.Scandium, Shapes.dust, 2),
                MaterialLibAPI.getStack(Materials.Quicklime, Shapes.dust, 5))
            .metadata(COIL_HEAT, 1814)
            .duration(12 * SECONDS)
            .eut(120)
            .addTo(blastFurnaceRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(MaterialLibAPI.getStack(Materials.Scandium, Shapes.dust, 4))
            .duration(40 * SECONDS)
            .eut(1920)
            .metadata(COIL_HEAT, 3650)
            .variants(
                variant -> variant.requiresEnvironment(RecipeEnvironments.NO_ATMOSPHERE)
                    .itemOutputs(MaterialLibAPI.getStack(Materials.Scandium, Shapes.ingotHot, 4)),
                variant -> variant.requiresEnvironment(RecipeEnvironments.ATMOSPHERE)
                    .itemOutputs(
                        MaterialLibAPI.getStack(Materials.Scandium, Shapes.ingotHot, 2),
                        MaterialLibAPI.getStack(CraterMaterials.scandiumOxide, Shapes.dust, 5)))
            .addTo(blastFurnaceRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(MaterialLibAPI.getStack(CraterMaterials.scandiumOxide, Shapes.dust, 5))
            .itemOutputs(MaterialLibAPI.getStack(Materials.Scandium, Shapes.dust, 2))
            .fluidOutputs(MaterialUtils.gas(Materials.Oxygen, 3000))
            .duration(80 * SECONDS)
            .eut(120)
            .addTo(electrolyzerRecipes);
        GTValues.RA.stdBuilder()
            .fluidInputs(new FluidStack(CraterMaterials.spentLeachSolution, 9000))
            .itemOutputs(MaterialLibAPI.getStack(Materials.SodiumNitrate, Shapes.dust, 27))
            .fluidOutputs(MaterialUtils.fluid(Materials.Water, 6000))
            .duration(90 * SECONDS)
            .eut(120)
            .addTo(distilleryRecipes);
    }

    private static ItemStack crushed(Material material, int amount) {
        return MaterialLibAPI.getStack(material, Shapes.crushed, amount);
    }
}
