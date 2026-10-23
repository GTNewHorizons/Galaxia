package com.gtnewhorizons.galaxia.gt5;

import static com.gtnewhorizons.galaxia.api.RecipeEnvironments.NO_ATMOSPHERE;
import static com.gtnewhorizons.galaxia.gt5.LunarMaterials.spaceGradeAlloy;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.recipe.RecipeMaps.blastFurnaceRecipes;
import static gregtech.api.recipe.RecipeMaps.fluidExtractionRecipes;
import static gregtech.api.recipe.RecipeMaps.fluidSolidifierRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.COIL_HEAT;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.ruling_0.materiallib.api.MaterialLibAPI;
import com.ruling_0.materiallib.api.Shape;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.materials.FluidShapes;
import gregtech.api.enums.materials.Materials;
import gregtech.api.enums.materials.Shapes;
import gregtech.api.enums.materials.TEBlockShapes;
import gregtech.api.util.GTModHandler;

/** Space Grade Alloy synthesis, casting and recovery through native GT recipe maps. */
public final class LunarRecipes {

    private LunarRecipes() {}

    public static void register() {
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

}
