package com.gtnewhorizons.galaxia.gt5.computer;

import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.recipe.RecipeMaps.vacuumFreezerRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTUtility.copyAmount;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.gt5.OrbitalItems;
import com.ruling_0.materiallib.api.MaterialLibAPI;

import gregtech.api.enums.Circuits;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.materials.Materials;
import gregtech.api.enums.materials.Shapes;
import gregtech.api.material.MaterialUtils;

/** Assembly of computing hardware from shared orbital materials and standard GT components. */
public final class ComputerRecipes {

    private static final String SPACE_GRADE_ALLOY = "SpaceGradeAlloy";

    private ComputerRecipes() {}

    /** Registers active cooling, then registers hardware only if every construction-alloy form is available. */
    public static void init() {
        GTValues.RA.stdBuilder()
            .fluidInputs(ComputerContent.neutral(1000))
            .fluidOutputs(ComputerContent.chilled(1000))
            .duration(5 * SECONDS)
            .eut(480)
            .addTo(vacuumFreezerRecipes);

        // GT can register recipes after omitting missing ore-dictionary ingredients.
        // Require the complete construction material before registering any hardware.
        List<String> missing = List.of("plate", "frameGt", "foil")
            .stream()
            .map(prefix -> prefix + SPACE_GRADE_ALLOY)
            .filter(
                name -> OreDictionary.getOres(name)
                    .isEmpty())
            .toList();
        if (!missing.isEmpty()) {
            Galaxia.LOG.error("Computer hardware recipes are unavailable. Missing ore entries: {}", missing);
            return;
        }

        GTValues.RA.stdBuilder()
            .itemInputs(
                new Object[] { "frameGt" + SPACE_GRADE_ALLOY, 1 },
                new Object[] { "plate" + SPACE_GRADE_ALLOY, 4 },
                ItemList.Circuit_Board_Plastic.get(2))
            .itemOutputs(new ItemStack(ComputerContent.hardware, 1, 3))
            .duration(20 * SECONDS)
            .eut(480)
            .addTo(assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(ComputerContent.hardware, 1, 3),
                new Object[] { "plate" + SPACE_GRADE_ALLOY, 2 },
                OrbitalItems.CLC_MODULE.get(2),
                new Object[] { Circuits.EV.getIngredient(), 2 },
                ItemList.Sensor_EV.get(1),
                ItemList.Emitter_EV.get(1))
            .itemOutputs(ComputerContent.controller())
            .duration(60 * SECONDS)
            .eut(1920)
            .addTo(assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(ComputerContent.hardware, 1, 3),
                new Object[] { "plate" + SPACE_GRADE_ALLOY, 2 },
                new Object[] { Circuits.EV.getIngredient(), 2 },
                ItemList.Electric_Pump_EV.get(2),
                OrbitalItems.FLUORIDE_FIBER_SPOOL.get(1))
            .itemOutputs(ComputerContent.cluster())
            .duration(45 * SECONDS)
            .eut(1920)
            .addTo(assemblerRecipes);
        racks();
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(ComputerContent.hardware, 1, 3),
                MaterialLibAPI.getStack(Materials.Copper, Shapes.plate, 4),
                MaterialLibAPI.getStack(Materials.Graphene, Shapes.dust, 4),
                ItemList.Electric_Pump_HV.get(2),
                new Object[] { Circuits.HV.getIngredient(), 1 })
            .itemOutputs(ComputerContent.radiator())
            .duration(30 * SECONDS)
            .eut(480)
            .addTo(assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(ComputerContent.hardware, 1, 3),
                new Object[] { Circuits.EV.getIngredient(), 1 },
                OrbitalItems.FLUORIDE_FIBER_SPOOL.get(1),
                MaterialLibAPI.getStack(Materials.Electrum, Shapes.wireFine, 8))
            .itemOutputs(ComputerContent.port())
            .duration(15 * SECONDS)
            .eut(1920)
            .addTo(assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                OrbitalItems.FLUORIDE_FIBER_SPOOL.get(1),
                MaterialLibAPI.getStack(Materials.Polytetrafluoroethylene, Shapes.plate, 2),
                new Object[] { "foil" + SPACE_GRADE_ALLOY, 4 })
            .itemOutputs(copyAmount(8, ComputerContent.cable()))
            .duration(10 * SECONDS)
            .eut(480)
            .addTo(assemblerRecipes);
    }

    /** Registers EV, IV and LuV racks with tiered electronics and Soldering Alloy. */
    private static void racks() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_EV.get(1),
                new Object[] { "plate" + SPACE_GRADE_ALLOY, 2 },
                OrbitalItems.CLC_MODULE.get(4),
                ItemList.Circuit_Chip_Ram.get(8),
                new Object[] { Circuits.EV.getIngredient(), 2 },
                OrbitalItems.FLUORIDE_FIBER_SPOOL.get(2))
            .fluidInputs(MaterialUtils.molten(Materials.SolderingAlloy, 576))
            .itemOutputs(new ItemStack(ComputerContent.hardware, 1, 0))
            .duration(60 * SECONDS)
            .eut(1920)
            .addTo(assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_IV.get(1),
                new Object[] { "plate" + SPACE_GRADE_ALLOY, 4 },
                OrbitalItems.CLC_MODULE.get(16),
                ItemList.Circuit_Chip_Ram.get(16),
                new Object[] { Circuits.IV.getIngredient(), 2 },
                OrbitalItems.FLUORIDE_FIBER_SPOOL.get(4))
            .fluidInputs(MaterialUtils.molten(Materials.SolderingAlloy, 1152))
            .itemOutputs(new ItemStack(ComputerContent.hardware, 1, 1))
            .duration(60 * SECONDS)
            .eut(7680)
            .addTo(assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_LuV.get(1),
                new Object[] { "plate" + SPACE_GRADE_ALLOY, 8 },
                OrbitalItems.CLC_MODULE.get(64),
                ItemList.Circuit_Chip_Ram.get(32),
                new Object[] { Circuits.LuV.getIngredient(), 2 },
                OrbitalItems.FLUORIDE_FIBER_SPOOL.get(8))
            .fluidInputs(MaterialUtils.molten(Materials.SolderingAlloy, 2304))
            .itemOutputs(new ItemStack(ComputerContent.hardware, 1, 2))
            .duration(60 * SECONDS)
            .eut(30720)
            .addTo(assemblerRecipes);
    }
}
