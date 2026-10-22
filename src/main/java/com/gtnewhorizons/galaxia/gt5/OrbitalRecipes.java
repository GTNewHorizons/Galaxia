package com.gtnewhorizons.galaxia.gt5;

import static com.gtnewhorizons.galaxia.gt5.OrbitalItems.*;
import static gregtech.api.recipe.RecipeMaps.*;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeConstants;
import gtPlusPlus.core.material.MaterialsElements;
import gtPlusPlus.core.material.nuclear.MaterialsFluorides;

/** Standard GT processing and assembly for the orbital material chains. */
public final class OrbitalRecipes {

    private OrbitalRecipes() {}

    public static void init() {
        siliconGermanium();
        fluorideGlass();
        solarComponents();
        satelliteParts();
        payloads();
    }

    private static void siliconGermanium() {
        GTValues.RA.stdBuilder()
            .itemInputs(ore(OrePrefixes.crushedPurified, Materials.Sphalerite, 1), Materials.Carbon.getDust(4))
            .fluidInputs(Materials.SulfuricAcid.getFluid(250))
            .fluidOutputs(MaterialsElements.getInstance().GERMANIUM.getFluidStack(8))
            .duration(30 * SECONDS)
            .eut(480)
            .addTo(GTRecipeConstants.UniversalChemical);
        GTValues.RA.stdBuilder()
            .itemInputs(Materials.SiliconSG.getDust(7))
            .fluidInputs(MaterialsElements.getInstance().GERMANIUM.getFluidStack(144))
            .itemOutputs(SIGE_BLEND.get(8))
            .duration(20 * SECONDS)
            .eut(120)
            .addTo(mixerRecipes);
        // TODO: add environment: MICROGRAVITY
        GTValues.RA.stdBuilder()
            .itemInputs(SIGE_BLEND.get(1))
            .itemOutputs(SIGE_SEED.get(1), POLY_SIGE.get(1), ore(OrePrefixes.dustTiny, Materials.Ash, 1))
            .outputChances(1000, 9000, 10000)
            .metadata(GTRecipeConstants.COIL_HEAT, 1784)
            .duration(60 * SECONDS)
            .eut(480)
            .addTo(blastFurnaceRecipes);
        // TODO: add environment: MICROGRAVITY
        GTValues.RA.stdBuilder()
            .itemInputs(SIGE_SEED.get(1))
            .fluidInputs(OrbitalItems.Melt.SIGE.get(1008))
            .itemOutputs(SIGE_MONOCRYSTAL.get(1))
            .metadata(GTRecipeConstants.COIL_HEAT, 1784)
            .duration(120 * SECONDS)
            .eut(480)
            .addTo(blastFurnaceRecipes);
        for (OrbitalItems feedstock : new OrbitalItems[] { POLY_SIGE, SIGE_SEED }) {
            GTValues.RA.stdBuilder()
                .itemInputs(feedstock.get(1))
                .fluidOutputs(OrbitalItems.Melt.SIGE.get(144))
                .duration(10 * SECONDS)
                .eut(480)
                .addTo(fluidExtractionRecipes);
        }
        GTValues.RA.stdBuilder()
            .itemInputs(SIGE_MONOCRYSTAL.get(1))
            .itemOutputs(SIGE_SEED.get(8))
            .duration(10 * SECONDS)
            .eut(120)
            .addTo(hammerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(SIGE_MONOCRYSTAL.get(1))
            .fluidInputs(Materials.Water.getFluid(100))
            .itemOutputs(SIGE_WAFER.get(16))
            .duration(40 * SECONDS)
            .eut(120)
            .addTo(cutterRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(SIGE_WAFER.get(1), ore(OrePrefixes.lens, Materials.Ruby, 0))
            .itemOutputs(CLC_WAFER.get(1))
            .requiresCleanRoom()
            .duration(45 * SECONDS)
            .eut(480)
            .addTo(laserEngraverRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(CLC_WAFER.get(1))
            .fluidInputs(Materials.HydrochloricAcid.getFluid(250))
            .itemOutputs(ETCHED_CLC_WAFER.get(1))
            .duration(30 * SECONDS)
            .eut(480)
            .addTo(chemicalBathRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(ETCHED_CLC_WAFER.get(1))
            .fluidInputs(Materials.Water.getFluid(100))
            .itemOutputs(CLC_CHIP.get(4))
            .requiresCleanRoom()
            .duration(45 * SECONDS)
            .eut(120)
            .addTo(cutterRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(CLC_CHIP.get(4), Materials.Copper.getPlates(2), ore(OrePrefixes.wireFine, Materials.Copper, 8))
            .fluidInputs(Materials.Tin.getMolten(144))
            .itemOutputs(CLC_MODULE.get(1))
            .duration(30 * SECONDS)
            .eut(480)
            .addTo(assemblerRecipes);
    }

    private static void fluorideGlass() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                MaterialsFluorides.SODIUM_FLUORIDE.getDust(2),
                Materials.AluminiumFluoride.getDust(4),
                Materials.SiliconDioxide.getDust(3))
            .itemOutputs(FLUORIDE_GLASS_MIX.get(9))
            .duration(15 * SECONDS)
            .eut(120)
            .addTo(mixerRecipes);
        // TODO: add environment: MICROGRAVITY
        GTValues.RA.stdBuilder()
            .itemInputs(FLUORIDE_GLASS_MIX.get(1))
            .fluidOutputs(OrbitalItems.Melt.FLUORIDE_GLASS.get(144))
            .metadata(GTRecipeConstants.COIL_HEAT, 1800)
            .duration(120 * SECONDS)
            .eut(480)
            .addTo(blastFurnaceRecipes);
        // TODO: add environment: MICROGRAVITY
        GTValues.RA.stdBuilder()
            .itemInputs(ItemList.Shape_Mold_Ingot.get(0))
            .fluidInputs(OrbitalItems.Melt.FLUORIDE_GLASS.get(288))
            .itemOutputs(FLUORIDE_GLASS_BLANK.get(1))
            .duration(20 * SECONDS)
            .eut(120)
            .addTo(fluidSolidifierRecipes);
        // TODO: add environment: MICROGRAVITY
        GTValues.RA.stdBuilder()
            .itemInputs(FLUORIDE_GLASS_PREFORM.get(1))
            .itemOutputs(HEATED_FLUORIDE_GLASS_PREFORM.get(1))
            .metadata(GTRecipeConstants.COIL_HEAT, 1800)
            .duration(20 * SECONDS)
            .eut(480)
            .addTo(blastFurnaceRecipes);
        // TODO: add environment: MICROGRAVITY
        GTValues.RA.stdBuilder()
            .itemInputs(HEATED_FLUORIDE_GLASS_PREFORM.get(1), ItemList.Shape_Extruder_Wire.get(0))
            .itemOutputs(FLUORIDE_OPTICAL_FIBER.get(8))
            .duration(30 * SECONDS)
            .eut(480)
            .addTo(extruderRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(FLUORIDE_GLASS_BLANK.get(1))
            .itemOutputs(FLUORIDE_GLASS_PREFORM.get(1), FLUORIDE_GLASS_MIX.get(1))
            .duration(20 * SECONDS)
            .eut(480)
            .addTo(latheRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(FLUORIDE_OPTICAL_FIBER.get(16))
            .itemOutputs(FLUORIDE_FIBER_SPOOL.get(1))
            .duration(10 * SECONDS)
            .eut(120)
            .addTo(assemblerRecipes);
    }

    private static void solarComponents() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                Materials.SiliconSG.getDust(64),
                Materials.Glass.getPlates(4),
                new Object[] { OrePrefixes.circuit.get(Materials.HV), 2 },
                Materials.Aluminium.getPlates(4),
                ore(OrePrefixes.wireFine, Materials.Copper, 8),
                MaterialsElements.getInstance().GERMANIUM.getDust(1),
                FLUORIDE_GLASS_MIX.get(9))
            .fluidInputs(Materials.Tin.getMolten(288))
            .itemOutputs(SPACE_GRADE_SOLAR_PANEL.get(1))
            .duration(120 * SECONDS)
            .eut(480)
            .addTo(assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(SIGE_WAFER.get(1), ore(OrePrefixes.lens, Materials.Glass, 0))
            .itemOutputs(SOLAR_CELL.get(4))
            .duration(45 * SECONDS)
            .eut(480)
            .addTo(laserEngraverRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(FLUORIDE_GLASS_BLANK.get(1))
            .fluidInputs(Materials.Water.getFluid(100))
            .itemOutputs(SOLAR_GLASS_SHEET.get(3))
            .duration(20 * SECONDS)
            .eut(120)
            .addTo(cutterRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                SOLAR_CELL.get(2),
                SOLAR_GLASS_SHEET.get(1),
                CLC_MODULE.get(1),
                Materials.Aluminium.getPlates(4),
                ore(OrePrefixes.wireFine, Materials.Copper, 8))
            .fluidInputs(Materials.Tin.getMolten(288))
            .itemOutputs(SPACE_GRADE_SOLAR_PANEL.get(1))
            .duration(60 * SECONDS)
            .eut(480)
            .addTo(assemblerRecipes);
    }

    private static void satelliteParts() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                Materials.Aluminium.getPlates(4),
                ore(OrePrefixes.stick, Materials.Steel, 2),
                ore(OrePrefixes.ring, Materials.Aluminium, 2))
            .itemOutputs(SATELLITE_FRAME.get(1))
            .duration(30 * SECONDS)
            .eut(480)
            .addTo(assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                Materials.Aluminium.getPlates(4),
                ore(OrePrefixes.wireFine, Materials.Copper, 4),
                ItemList.Emitter_MV.get(1))
            .fluidInputs(Materials.Tin.getMolten(144))
            .itemOutputs(new ItemStack(GalaxiaItemList.SATELLITE_DISH.getItem()))
            .duration(20 * SECONDS)
            .eut(480)
            .addTo(assemblerRecipes);
    }

    private static void payloads() {
        ItemStack dish = new ItemStack(GalaxiaItemList.SATELLITE_DISH.getItem());
        GTValues.RA.stdBuilder()
            .itemInputs(
                SATELLITE_FRAME.get(1),
                dish,
                ore(OrePrefixes.lens, Materials.Glass, 4),
                new Object[] { OrePrefixes.circuit.get(Materials.HV), 1 },
                ItemList.Sensor_MV.get(1),
                ItemList.Battery_RE_HV_Lithium.get(1),
                ItemList.Cover_SolarPanel.get(1))
            .fluidInputs(Materials.Tin.getMolten(144))
            .itemOutputs(PRIMITIVE_TELESCOPE_SATELLITE.get(1))
            .duration(45 * SECONDS)
            .eut(480)
            .addTo(assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                SATELLITE_FRAME.get(1),
                new ItemStack(dish.getItem(), 2),
                ItemList.Emitter_HV.get(3),
                ItemList.Sensor_HV.get(1),
                CLC_MODULE.get(1),
                FLUORIDE_FIBER_SPOOL.get(2),
                ItemList.Battery_RE_HV_Lithium.get(1),
                SPACE_GRADE_SOLAR_PANEL.get(2))
            .fluidInputs(Materials.Tin.getMolten(288))
            .itemOutputs(COMMUNICATION_SATELLITE.get(1))
            .duration(60 * SECONDS)
            .eut(480)
            .addTo(assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                SATELLITE_FRAME.get(1),
                ItemList.Emitter_HV.get(1),
                ItemList.Sensor_HV.get(3),
                ore(OrePrefixes.lens, Materials.Diamond, 2),
                CLC_MODULE.get(2),
                FLUORIDE_FIBER_SPOOL.get(1),
                ItemList.Battery_RE_HV_Lithium.get(1),
                SPACE_GRADE_SOLAR_PANEL.get(1))
            .fluidInputs(Materials.Tin.getMolten(288))
            .itemOutputs(PROSPECTING_SATELLITE.get(1))
            .duration(60 * SECONDS)
            .eut(480)
            .addTo(assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                CLC_MODULE.get(1),
                new Object[] { OrePrefixes.circuit.get(Materials.HV), 2 },
                ItemList.Sensor_HV.get(1),
                ItemList.Emitter_HV.get(1),
                Materials.Aluminium.getPlates(2),
                ore(OrePrefixes.wireFine, Materials.Copper, 4))
            .fluidInputs(Materials.Tin.getMolten(288))
            .itemOutputs(ROCKET_AUTOMATION_CONTROLLER.get(1))
            .duration(45 * SECONDS)
            .eut(480)
            .addTo(assemblerRecipes);
    }

    private static ItemStack ore(OrePrefixes prefix, Materials material, int amount) {
        return GTOreDictUnificator.get(prefix, material, amount);
    }
}
