package com.gtnewhorizons.galaxia.gt5;

import static com.gtnewhorizons.galaxia.api.RecipeEnvironments.NO_ATMOSPHERE;
import static com.gtnewhorizons.galaxia.gt5.LunarMaterials.spaceGradeAlloy;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.recipe.RecipeMaps.blastFurnaceRecipes;
import static gregtech.api.recipe.RecipeMaps.centrifugeRecipes;
import static gregtech.api.recipe.RecipeMaps.chemicalReactorRecipes;
import static gregtech.api.recipe.RecipeMaps.distilleryRecipes;
import static gregtech.api.recipe.RecipeMaps.electrolyzerRecipes;
import static gregtech.api.recipe.RecipeMaps.fluidExtractionRecipes;
import static gregtech.api.recipe.RecipeMaps.fluidSolidifierRecipes;
import static gregtech.api.recipe.RecipeMaps.multiblockChemicalReactorRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.COIL_HEAT;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.compat.gt.crater.MTECraterExtractor;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterProfile;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.ruling_0.materiallib.api.Material;
import com.ruling_0.materiallib.api.MaterialLibAPI;
import com.ruling_0.materiallib.api.Shape;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Circuits;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.materials.FluidShapes;
import gregtech.api.enums.materials.Materials;
import gregtech.api.enums.materials.Shapes;
import gregtech.api.enums.materials.TEBlockShapes;
import gregtech.api.material.MaterialUtils;
import gregtech.api.util.GTModHandler;

/** Initial lunar extraction and refining recipes using ordinary GT recipe maps. */
public final class LunarRecipes {

    private LunarRecipes() {}

    public static ItemStack[] solids(CraterProfile profile) {
        return switch (profile) {
            case IRON_SILICA -> new ItemStack[] { crushed(Materials.BandedIron, 8), crushed(Materials.Quartzite, 2) };
            case GOLD_COBALT -> new ItemStack[] { crushed(Materials.Gold, 2), crushed(Materials.Cobaltite, 1) };
            case SCANDIUM_BAUXITE -> new ItemStack[] { crushed(Materials.Bauxite, 8),
                MaterialLibAPI.getStack(LunarMaterials.scandiumConcentrate, Shapes.dust, 1) };
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
                .fluidInputs(new FluidStack(LunarMaterials.enriched(profile), 16000))
                .itemOutputs(solids(profile))
                .fluidOutputs(new FluidStack(LunarMaterials.spentCarrier, 16000))
                .duration(40 * SECONDS)
                .eut(120)
                .addTo(centrifugeRecipes);
        }
        GTValues.RA.stdBuilder()
            .fluidInputs(new FluidStack(LunarMaterials.spentCarrier, 4000))
            .fluidOutputs(new FluidStack(ItemList.sDrillingFluid, 4000))
            .duration(8 * SECONDS)
            .eut(120)
            .addTo(centrifugeRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(MaterialLibAPI.getStack(LunarMaterials.scandiumConcentrate, Shapes.dust, 12))
            .fluidInputs(MaterialUtils.fluid(Materials.NitricAcid, 6000), MaterialUtils.fluid(Materials.Water, 3000))
            .itemOutputs(MaterialLibAPI.getStack(Materials.Stone, Shapes.dust, 10))
            .fluidOutputs(new FluidStack(LunarMaterials.scandiumSolution, 9000))
            .duration(60 * SECONDS)
            .eut(480)
            .addTo(multiblockChemicalReactorRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(MaterialLibAPI.getStack(Materials.SodiumHydroxide, Shapes.dust, 18))
            .fluidInputs(new FluidStack(LunarMaterials.scandiumSolution, 9000))
            .itemOutputs(MaterialLibAPI.getStack(LunarMaterials.scandiumOxide, Shapes.dust, 5))
            .fluidOutputs(new FluidStack(LunarMaterials.spentLeachSolution, 9000))
            .duration(30 * SECONDS)
            .eut(120)
            .addTo(chemicalReactorRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                MaterialLibAPI.getStack(LunarMaterials.scandiumOxide, Shapes.dust, 5),
                MaterialLibAPI.getStack(Materials.Calcium, Shapes.dust, 3))
            .itemOutputs(
                MaterialLibAPI.getStack(Materials.Scandium, Shapes.dust, 2),
                MaterialLibAPI.getStack(Materials.Quicklime, Shapes.dust, 5))
            .metadata(COIL_HEAT, 1814)
            .duration(12 * SECONDS)
            .eut(120)
            .addTo(blastFurnaceRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(MaterialLibAPI.getStack(LunarMaterials.scandiumOxide, Shapes.dust, 5))
            .itemOutputs(MaterialLibAPI.getStack(Materials.Scandium, Shapes.dust, 2))
            .fluidOutputs(MaterialUtils.gas(Materials.Oxygen, 3000))
            .duration(80 * SECONDS)
            .eut(120)
            .addTo(electrolyzerRecipes);
        GTValues.RA.stdBuilder()
            .fluidInputs(new FluidStack(LunarMaterials.spentLeachSolution, 9000))
            .itemOutputs(MaterialLibAPI.getStack(Materials.SodiumNitrate, Shapes.dust, 27))
            .fluidOutputs(MaterialUtils.fluid(Materials.Water, 6000))
            .duration(90 * SECONDS)
            .eut(120)
            .addTo(distilleryRecipes);
        registerSpaceGradeAlloy();
    }

    private static void registerSpaceGradeAlloy() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                MaterialLibAPI.getStack(Materials.Iron, Shapes.ingot, 10),
                MaterialLibAPI.getStack(Materials.Silicon, Shapes.dust, 1))
            .fluidOutputs(MaterialLibAPI.getFluidStack(spaceGradeAlloy, FluidShapes.fluidMolten, 288))
            .metadata(COIL_HEAT, 3600)
            .requiresEnvironment(NO_ATMOSPHERE)
            .duration(40 * SECONDS)
            .eut(480)
            .addTo(blastFurnaceRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                MaterialLibAPI.getStack(Materials.Gold, Shapes.ingot, 5),
                MaterialLibAPI.getStack(Materials.Cobalt, Shapes.ingot, 2))
            .fluidOutputs(MaterialLibAPI.getFluidStack(spaceGradeAlloy, FluidShapes.fluidMolten, 576))
            .metadata(COIL_HEAT, 4500)
            .requiresEnvironment(NO_ATMOSPHERE)
            .duration(80 * SECONDS)
            .eut(480)
            .addTo(blastFurnaceRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                MaterialLibAPI.getStack(Materials.Aluminium, Shapes.ingot, 10),
                MaterialLibAPI.getStack(Materials.Scandium, Shapes.ingot, 1))
            .fluidOutputs(MaterialLibAPI.getFluidStack(spaceGradeAlloy, FluidShapes.fluidMolten, 1584))
            .metadata(COIL_HEAT, 5400)
            .requiresEnvironment(NO_ATMOSPHERE)
            .duration(220 * SECONDS)
            .eut(480)
            .addTo(blastFurnaceRecipes);
        cast(Shapes.ingot, ItemList.Shape_Mold_Ingot.get(0), 144, 1);
        cast(Shapes.plate, ItemList.Shape_Mold_Plate.get(0), 144, 1);
        cast(Shapes.stick, ItemList.Shape_Mold_Rod.get(0), 72, 1);
        cast(Shapes.foil, new ItemStack(GalaxiaItemList.THIN_STRIP_MOLD.getItem(), 0), 36, 4);
        GTValues.RA.stdBuilder()
            .itemInputs(
                MaterialLibAPI.getStack(spaceGradeAlloy, Shapes.plate, 1),
                MaterialLibAPI.getStack(spaceGradeAlloy, Shapes.stick, 2))
            .itemOutputs(MaterialLibAPI.getStack(spaceGradeAlloy, TEBlockShapes.frameGt, 1))
            .duration(5 * SECONDS)
            .eut(120)
            .addTo(assemblerRecipes);
        melt(TEBlockShapes.frameGt, 288);
        GTModHandler.addCraftingRecipe(
            new ItemStack(GalaxiaItemList.THIN_STRIP_MOLD.getItem()),
            new Object[] { "fP", 'P', ItemList.Shape_Empty.get(1) });
    }

    private static void cast(Shape shape, ItemStack mold, int volumePerItem, int amount) {
        int volume = volumePerItem * amount;
        GTValues.RA.stdBuilder()
            .itemInputs(mold)
            .fluidInputs(MaterialLibAPI.getFluidStack(spaceGradeAlloy, FluidShapes.fluidMolten, volume))
            .itemOutputs(MaterialLibAPI.getStack(spaceGradeAlloy, shape, amount))
            .requiresEnvironment(NO_ATMOSPHERE)
            .duration(volume * 5 * SECONDS / 144)
            .eut(120)
            .addTo(fluidSolidifierRecipes);
        melt(shape, volumePerItem);
    }

    private static void melt(Shape shape, int volume) {
        GTValues.RA.stdBuilder()
            .itemInputs(MaterialLibAPI.getStack(spaceGradeAlloy, shape, 1))
            .fluidOutputs(MaterialLibAPI.getFluidStack(spaceGradeAlloy, FluidShapes.fluidMolten, volume))
            .requiresEnvironment(NO_ATMOSPHERE)
            .duration(volume * 5 * SECONDS / 144)
            .eut(120)
            .addTo(fluidExtractionRecipes);
    }

    private static ItemStack crushed(Material material, int amount) {
        return MaterialLibAPI.getStack(material, Shapes.crushed, amount);
    }
}
